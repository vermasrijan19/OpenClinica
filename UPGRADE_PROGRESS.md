# Spring upgrade: progress and handoff

Last updated 2026-11-26. This is a working document for whoever continues the upgrade.

## Goal and constraints (decided by the repo owner)

- Upgrade the stack to **Spring Boot 4.2.0** (Spring Framework 7.1, Spring Security 7.2,
  Hibernate 7.4, Jackson 3, Spring-WS 5.1, Liquibase 5), deployed as a **WAR on Tomcat 11**.
- **Java only. No Kotlin conversion.**
- **Keep the 419 JSPs** (move them to the `jakarta.*` namespace in Stage 4).
- **Commit each stage directly on `master`** and push to `origin` (the owner's fork,
  `github.com/vermasrijan19/OpenClinica`). There is no upstream remote and nothing goes upstream.
- **PostgreSQL only.** Oracle support is not needed, so migrations can be Postgres-only.
- Preserve behaviour: every stage is checked against the previous one (see "Verification").

## Stages

| Stage | Status | Commit |
|---|---|---|
| 0: smoke test, DB reset script, configurable test DB | done | `26e0da16c`, `1c2f551b5` |
| 1: Java 8 target, Spring 4.3, Security 4.2, Hibernate 3.6, Quartz 2.3 (+ DB migration), Jackson 2, Spring-WS 2.4 | done | `af087edca` |
| 2a: PostgreSQL JDBC 9.1 → 42.7.15 | done | `760cda7ff` |
| 2b: Spring 5.3.39, Security 5.8.16, Hibernate 5.6.15, Spring-WS 3.1.8, OAuth2 2.5.2 | done | `27f027224` |
| 2c: Jersey 1 → Spring MVC | done | `bbab821a5` |
| **3: JDK 8 → 21** | **next** | |
| 4: `javax` → `jakarta`, Spring Boot 4.2 as a WAR, Tomcat 8.5 → 11, Hibernate 7, Security 7, Jackson 3, Spring-WS 5, Liquibase 5, XML config → Java config | todo | |

Read the commit messages of the done stages. They list every behaviour-preserving decision.

## Local environment

- Built with **JDK 8** (`~/opt/jdk8u514-b01`) and the Maven bundled with IntelliJ:
  `/home/sverma/.local/share/JetBrains/Toolbox/apps/intellij-idea-2/plugins/maven-plugin/lib/maven3/bin/mvn`.
  Example: `JAVA_HOME=~/opt/jdk8u514-b01 $MVN -B -o clean package -DskipTests`.
- Runs on **Tomcat 8.5.100** at `~/opt/apache-tomcat-8.5.100` (port 8085) as the systemd user
  service `openclinica`: `systemctl --user restart openclinica`, logs via `journalctl --user -u openclinica`
  and `~/opt/apache-tomcat-8.5.100/logs/localhost.*.log`. Don't use `startup.sh` or `shutdown.sh`.
- `webapps/OpenClinica.war` and `OpenClinica-ws.war` are symlinks. Normally they point to the last
  verified build in `~/opt/openclinica-stable/` (currently Stage 2c). `scripts/upgrade-tools/redeploy.sh`
  repoints them at `web/target` and `ws/target` for testing. Re-pin to stable copies when a stage is done.
- DB: host Postgres 18, database `openclinica`, user `clinica` / password `clinica` (md5 auth, `localhost:5432`).
  The DB holds **no real data**. Reset it freely with `scripts/reset-db.sh --yes`, after which
  `root` / `12345678` works again.
- The unit tests run against the main DB and overwrite tables (DBUnit CLEAN_INSERT), so **reset the DB
  after running them**: `mvn test -Dmaven.test.failure.ignore=true -Ddb.test.name=openclinica`.
- The repo mixes CRLF and LF line endings per file. Preserve each file's endings: edit with
  `sed` or Python `open(..., newline='')`, and check with `git diff --stat` that only real lines changed.

## Verification used for every stage

1. Unit tests: **39/39 pass** as of 2b.
2. `OC_USER=root OC_PASS=12345678 scripts/smoke-test.sh`: **28/28** (pages, static files with gzip,
   5 SOAP WSDLs, a Quartz job create/pause/resume/delete round trip).
3. `scripts/upgrade-tools/crawl.py http://localhost:8085 root 12345678 400 > crawl.tsv`: a logged-in
   GET crawl (63 pages). Diff against the previous build's output. 2b was identical to 2a.
4. `scripts/upgrade-tools/rest_capture.sh OUTDIR`: status, content type and normalized body hash of the
   Jersey REST endpoints, as `root` and as a monitor-role user (`smokemonitor`, created automatically).
   Run it on a freshly reset DB for both builds and diff the `summary.tsv` files.
5. SOAP: set `run_webservices=true` for root, then POST a `listAllRequest` to
   `/OpenClinica-ws/ws/study/v1` with a WS-Security UsernameToken whose password is the **stored hash**
   (md5 of `12345678` for root). Expect `<result>Success</result>`; a wrong password must be rejected.
6. For Hibernate changes: `scripts/upgrade-tools/mapdump/` dumps every entity's tables, columns,
   collections and id generators (`DumpH3` for Hibernate 3.6, `DumpH5` for 5.6). `mapping-h3.txt` is the
   original baseline; the only allowed differences are synthetic `_...Backref` property names.
   Port the dumper to Hibernate 7 for Stage 4 and diff again.

Helper scripts in `scripts/upgrade-tools/` were written ad hoc. `redeploy.sh` and `redeploy-stable.sh`
use `$S` (default `/tmp/oc-upgrade`) for logs; `rest_capture.sh` and `crawl.py` take output paths as arguments.

## Stage 2c design: Jersey 1 → Spring MVC (next step)

Jersey 1 can't move to `jakarta`, so it must go before Stage 4. What exists:

- `web/.../web/restful/ODMMetadataRestResource.java` (`/rest/metadata/...`) and
  `ODMClinicaDataResource.java` (`/rest/clinicaldata/...`), served by the `OpenClinicaJersey` servlet on `/rest/*`.
- `web/.../web/pform/OpenRosaServices.java` (`/rest2/openrosa/...`), served by `OpenClinicaJersey2` on `/rest2/*`.
- `web/.../web/filter/rest/RestODMFilter.java`, a Jersey ContainerRequestFilter on `/rest/*` only.
- The three resources are XML beans in `web/src/main/resources/org/akaza/openclinica/applicationContext-security.xml`
  (ids `odmMetadataRestResource`, `odmClinicalDataRestResource`, `openRosaServices`), referenced nowhere else.
- `BatchCRFMigrationController` only imports `javax.ws.rs` constants (`MediaType`, `Produces`); replace those with Spring's.

Plan:
1. In `web.xml`, replace both Jersey servlets with `DispatcherServlet`s on the same URL patterns, each with
   its own small context XML that defines its resource beans (moved out of the root XML; dependencies are
   refs to root beans) and an explicit `RequestMappingHandlerMapping`. Leave suffix pattern matching off,
   since Jersey doesn't do it. This way no other controller becomes reachable under `/rest` or `/rest2`.
2. Annotate the resources `@Controller` + `@RequestMapping` with the same paths. Keep each Jersey method
   body unchanged; add a thin Spring-annotated wrapper that reproduces Jersey's response semantics:
   - A `String` method returning `null` → **HTTP 204** with the `@Produces` content type set
     (the baseline shows 204 for `formList`, `manifest` and `formXml` on an empty DB).
   - Text and JSON responses with `charset=UTF-8` (Spring's String converter defaults to ISO-8859-1).
   - `@QueryParam` → `@RequestParam(required=false)`. Apply `@DefaultValue("n")` by hand only when the
     parameter is **absent**: Spring's `defaultValue` also replaces empty values, Jersey's doesn't.
   - The unused `@RequestHeader("Authorization") String authorization` params (Spring annotations that
     Jersey ignored) → `required=false`.
   - `Viewable("/WEB-INF/jsp/printcrf.jsp", null)` → `ModelAndView("/WEB-INF/jsp/printcrf.jsp")`
     (the JSP reads request attributes).
   - `javax.ws.rs.core.Response` / `StreamingOutput` (media download, `getPdf2`, submission + HEAD)
     → `ResponseEntity`, keeping the same statuses (201/202/204/406/500) and headers.
3. Port `RestODMFilter` to a `HandlerInterceptor` on the `/rest` dispatcher. Study OID = 4th path segment
   of `request.getPathInfo()`; fewer segments must still give 500 (baseline: `/rest/metadata/nosuchthing` → 500).
   On denial, set the `PAGE_MESSAGE` attribute and call **`response.sendError(403)`**: Jersey's 403
   renders the container's full HTML error page (~19 KB in the baseline), not an empty body.
4. Remove the Jersey dependencies (`jersey-server`, `jersey-client`, `jersey-spring` in `web/pom.xml`)
   and make sure nothing else imports `com.sun.jersey` or `javax.ws.rs`.
5. Verify with `rest_capture.sh` on a reset DB and diff against the 2b baseline in
   `scripts/upgrade-tools/baseline-rest-2b/` (`summary.tsv` plus normalized bodies, captured on a fresh DB).
   The 2b crawl baseline is `scripts/upgrade-tools/baseline-crawl-2b.tsv`.
   Also re-run the smoke test, crawl and unit tests.

## Stage 3 / 4 notes and known items

- Stage 3 (JDK 21): Spring 5.3, Hibernate 5.6, Quartz 2.3 and Tomcat 8.5 all support it. Watch for
  `--add-opens` needs (old Castor, cglib-nodep 2.1, Saxon 8.7, POI 3.0.1) and for removed Java EE modules
  (JAXB, `javax.xml.ws`, activation). Add explicit dependencies for those.
- Stage 4: Spring Security 7 drops `use-expressions="false"` style XML config. The current XML deliberately
  keeps 3.2 behaviour: CSRF off (no tokens in the JSPs), default headers off, max 1 session per user,
  the custom login filter at `/j_spring_security_check` with `j_username`/`j_password`, and
  `NoOpPasswordEncoder` for SOAP tokens.
- Passwords are unsalted SHA-1 (new) or MD5 (old) hex. `core/.../core/LegacyDigestPasswordEncoder` must
  keep that exact format: SOAP clients send the stored hash.
- Spring Security OAuth 2.5.2 (XML `oauth:` namespace, used in both security XMLs) is end-of-life and won't
  run on Spring 6/7. It needs a replacement decision in Stage 4. Check whether anything actually uses the token endpoints.
- Spring-WS 5 removes XWSS (`XwsSecurityInterceptor`, used for SOAP auth) and the deprecated
  `PayloadRootAnnotationMethodEndpointMapping` / `GenericMarshallingMethodEndpointAdapter` /
  `XPathParamAnnotationMethodEndpointAdapter` used in `ws/.../WEB-INF/ws-servlet-config.xml`. Plan a move to
  WSS4J and annotation-driven endpoints.
- Quartz job tables were migrated to the 2.x layout by `core/src/main/resources/migration/3.18/2026-11-26-quartz2-schema.xml`.
  The scheduler name is fixed to `schedulerFactoryBean`.
- Pre-existing issues, not regressions: the web WAR maps `/ws/*` to Spring-WS's `MessageDispatcherServlet`
  without the Spring-WS jars, and `WEB-INF/security-config.xml` in web and ws is dead config.
- Untested so far: LDAP login, OAuth2 token endpoints, and pages that need real study data (CRFs, subjects).
  Only the default empty study exists in the DB.
