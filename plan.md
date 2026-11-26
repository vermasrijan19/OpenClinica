# OpenClinica Modernization Plan: JDK 21/25 + Latest Stack (Spring 7, Hibernate 7, Jakarta EE 11)

## 1. Executive Summary & Version Targets

This plan upgrades OpenClinica (`3.18-SNAPSHOT`) from **Java 8 + Java EE (`javax.*`) + Tomcat 8.5** to **Java 21 LTS (ready for Java 25 LTS) + Jakarta EE 11 + Tomcat 11** and the latest stable releases of all core dependencies.

> **Important Prerequisite:** Commit or stash your current working-tree UI/outbound-link cleanup changes on `master` before starting Phase 1 so every upgrade step has a clean git baseline and rollback point.

> **Decided target:** **Spring Boot `4.2.0`** (parent POM or imported `spring-boot-dependencies` BOM) packaged as **WARs on Tomcat 11** via `SpringBootServletInitializer`, JSPs kept, code stays Java. Versions in the table below are what the Boot 4.2 BOM manages; only pin a version explicitly where Boot does not manage the artifact.

| Component | Current Version | Target Version | Notes / Breaking Change Driver |
| :--- | :--- | :--- | :--- |
| **JDK** | Java `1.8` (`jdk8u514-b01`) | **Java 21 LTS** (Phase 1) → **Java 25 LTS** (Phase 2+) | Spring 7 / Hibernate 7 / Liquibase 5 require Java 17+. `java-25-openjdk` (`25.0.5`) is already installed on this host; Java 21 LTS is needed during the intermediate Spring 5.3 step. |
| **Servlet Container** | Tomcat `8.5.100` (`javax.servlet` 3.1 / `web.xml` 2.4) | **Tomcat 11.0.x** (Jakarta Servlet 6.1) | Required for `jakarta.*` namespace. |
| **Spring Framework** | `5.3.39` | **`7.1.0`** *(via Spring Boot `4.2.0` BOM)* | Moves to `jakarta.*`; drops `orm.hibernate5`. |
| **Spring Security** | `5.8.16` | **`7.2.0`** | Moves to `jakarta.*`; drops `use-expressions="false"` XML syntax. |
| **Spring Security OAuth2** | `2.5.2.RELEASE` | **Remove dead config** or **Spring Authorization Server** (bundled in Security 7) | `spring-security-oauth2` is EOL and incompatible with Spring 6/7. |
| **Spring-WS** | `3.1.8` | **`5.1.0`** | Drops `XwsSecurityInterceptor` and legacy endpoint mappings/adapters. |
| **Hibernate ORM** | `5.6.15.Final` | **`7.4.17.Final`** (`org.hibernate.orm:hibernate-core`) | `jakarta.persistence`; removes `Criteria`, `save/update/saveOrUpdate`, `hibernate-ehcache`. |
| **Hibernate Validator** | `6.2.5.Final` | **`9.x`** (or Spring Boot BOM) | `jakarta.validation` 3.1. |
| **Jackson** | `2.12.7.1` (`com.fasterxml.jackson`) | **`2.21.x`** for the cutover → **`3.3.0`** (`tools.jackson.*`) as a follow-up | Spring 7 still supports Jackson 2, so Jackson 3 is off the critical path. |
| **Liquibase** | `4.3.5` (+ `liquibase-plugin` `1.9.5.0`) | **`4.33.x`** (recommended, Apache-2.0) *or `5.0.4`* (FSL) | Liquibase 5 changed license to FSL. Checksum algorithm changes after 4.3.5 (see Phase 6). |
| **Quartz Scheduler** | `2.3.2` | **`2.5.2`** | No servlet coupling in this repo — can be bumped in Phase 1, before the cutover. |
| **PostgreSQL JDBC** | `42.7.15` | **`42.7.15`** (already latest) | Already Java 8–25 compatible. |
| **SiteMesh** | `2.3` (`opensymphony:sitemesh`) | **`3.3.0`** (`org.sitemesh:sitemesh`) | Adds native Jakarta Servlet 6.1 / Tomcat 11 / Spring Boot 4 support. |

---

## 2. Key Architectural Coupling (Why Phasing Matters)

1. **You cannot jump directly to Spring 7 / Hibernate 7 while keeping Tomcat 8.5 (`javax.*`).**
   Spring 6+, Spring Security 6+, Spring-WS 4+, Hibernate 6.2+/7+, and SiteMesh 3.2+ all require **`jakarta.*`** (Tomcat 10.1 / 11).
2. **You should upgrade the JDK before the `javax` → `jakarta` cutover.**
   Doing **JDK 8 → JDK 21** while keeping Spring 5.3 / Hibernate 5.6 / Tomcat 8.5 isolates Java module/reflection/JAXB issues from Servlet/JPA/Spring breaking changes.
3. **Once you switch to `jakarta.*`, Tomcat 11, Spring 7.1, Spring Security 7.2, Spring-WS 5.1, Hibernate 7.4, and SiteMesh 3.3 must move together** (one cutover branch, managed by the **Spring Boot 4.2.0** BOM, deployed as WARs on Tomcat 11). Everything that *can* move earlier (Quartz 2.5, `cglib` removal, Jackson 2.21, build plugins) should, to keep the cutover small.
4. **The cutover still needs interim gates** — see "Cutover Checkpoints" before Phase 2.

---

## 3. Existing Verification Harness to Use at Every Phase

Reuse the repo's verification tools on a freshly reset local PostgreSQL database (`openclinica`, user/pass `clinica`/`clinica`):

1. **DB Reset:** `scripts/reset-db.sh --yes`
2. **Compile & Unit Tests:** `mvn test -Dmaven.test.failure.ignore=true -Ddb.test.name=openclinica` (reset DB afterward because DBUnit runs `CLEAN_INSERT`).
3. **Deploy:** `scripts/upgrade-tools/redeploy.sh` (update this script for Tomcat 11 in Phase 2).
4. **Smoke Tests:** `OC_USER=root OC_PASS=12345678 scripts/smoke-test.sh` (pages, gzip static assets, 5 SOAP WSDLs, Quartz CRUD round trip).
5. **Logged-in UI Crawl:** `scripts/upgrade-tools/crawl.py http://localhost:8085 root 12345678 400 > crawl.tsv` and diff against `scripts/upgrade-tools/baseline-crawl-2b.tsv`.
6. **REST Capture:** `scripts/upgrade-tools/rest_capture.sh /tmp/rest-out` and diff against `scripts/upgrade-tools/baseline-rest-2b/`.
7. **SOAP Security Check:** Enable `run_webservices=true` for `root` and POST a `listAllRequest` to `/OpenClinica-ws/ws/study/v1` with WS-Security `UsernameToken` (using stored MD5 hash for valid auth and verifying bad password rejection).
8. **Hibernate Schema/Mapping Audit:** Port `scripts/upgrade-tools/mapdump` (`DumpH5`) to Hibernate 7 and diff against the baseline mapping dump.
9. **ID Generation Round Trip (from Phase 3 on):** `mapdump` only shows metadata and will not catch runtime generator changes. Insert at least one row through Hibernate for each generator-bearing entity (e.g. create a user, study, CRF, rule, audit login) and assert the new ID equals `currval` of the entity's existing PostgreSQL sequence (no jump by 50, no `hibernate_sequence` / `<entity>_seq` lookups in the SQL log).
10. **JDK pinning:** The host default `java` is **26**. Set `JAVA_HOME` explicitly in `redeploy.sh`, the `openclinica` systemd user unit, and Maven (`.mvn/jvm.config` or toolchains) so no step silently runs on 26.

---

## 4. Phase-by-Phase Migration Plan

### Phase 0: Baseline Hygiene & Low-Risk Prep (Still on Java 8)
- **Commit pending work** on `master` (outbound link / advertisement removals) and capture fresh `crawl.tsv` and `rest_capture.sh` baselines.
- **Pick one handoff doc:** `UPGRADE_PROGRESS.md` is staged as deleted while `plan.md` is untracked. Either commit `plan.md` as its replacement or restore `UPGRADE_PROGRESS.md` and fold this plan into it, before committing the baseline.
- **Upgrade Jackson 2.x immediately** (optional quick win): `2.12.7.1` → `2.21.9` (LTS) in `pom.xml` for security patches while still on Java 8.
- **Upgrade Maven toolchain compatibility:** Update `pom.xml` build plugins:
  - `maven-compiler-plugin` `2.3.2` → `3.13.0`
  - `maven-resources-plugin` `2.5` → `3.3.1`
  - `maven-surefire-plugin` `2.10` → `3.5.x`
  - `maven-war-plugin` → `3.4.0`
  - Remove dead `jrebel-maven-plugin`, `cargo-maven2-plugin` (`tomcat6x`), and `maven-eclipse-plugin` if unused.
- **Check `ws` vs `web` dead configs:** Note that `web/src/main/webapp/WEB-INF/web.xml` maps `/ws/*` to `MessageDispatcherServlet` without Spring-WS jars, and `WEB-INF/security-config.xml` in both `web/` and `ws/` is dead config—remove in Phase 4/5.

---

### Phase 1: JDK 8 → JDK 21 LTS (Keep Tomcat 8.5 + Spring 5.3 + Hibernate 5.6)
> **Why JDK 21 before JDK 25:** Spring `5.3.39` bundles ASM 9.x that fails scanning Java 25 (`class file version 69`) classes. Note that `/usr/lib/jvm/java-21-openjdk` on this machine currently has no `bin/java` binary (and the default `java` is 26), so install OpenJDK 21 LTS (e.g., `sudo dnf install java-21-openjdk-devel` or unpack under `~/opt/`) and pin `JAVA_HOME` everywhere (see Verification §10).

1. **Compiler target:** Update `pom.xml` `maven-compiler-plugin` to `<release>21</release>`.
2. **Restore Java EE APIs removed from JDK 11+:** Add explicit dependencies (until moving to `jakarta.*` in Phase 2):
   - `jakarta.xml.bind:jakarta.xml.bind-api:2.3.3` + `org.glassfish.jaxb:jaxb-runtime:2.3.9`
   - `com.sun.activation:jakarta.activation:1.2.2`
   - `javax.xml.ws:jaxws-api:2.3.1`, `javax.jws:javax.jws-api:1.1`, `javax.xml.soap:javax.xml.soap-api:1.4.0`
   - Replace ancient `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin:0.7.5` (`ws/pom.xml` / `pom.xml`) with `org.jvnet.jaxb:jaxb-maven-plugin:2.0.14` (or `4.x` in Phase 2).
3. **Bytecode & reflection compatibility:**
   - **Delete `cglib:cglib-nodep:2.1_3`** from `core/pom.xml`, `web/pom.xml`, and `ws/pom.xml`. No code references `net.sf.cglib`; Spring 5.3 uses its repackaged CGLIB and Hibernate 5.6 uses ByteBuddy.
   - **Do not add `--add-opens` speculatively.** Add each flag (Surefire `argLine` and Tomcat `setenv.sh`) only when a concrete `InaccessibleObjectException` requires it, and record in a comment which library/stack trace needed it, so the list can be pruned after the cutover.
   - Upgrade `junit` (`4.11` → `4.13.2` or JUnit 5 vintage) and `mockito-all` (`1.9.5` → `mockito-core` `5.x`) if reflection/byte-generation fails on JDK 21.
   - Check old libraries under JDK 21: `castor` (`1.2` / `1.3.1` / `1.3.3`), `saxon` (`8.7`), `poi` (`3.0.1-FINAL`), `jxl` (`2.6.10`), `xom` (`1.2.5`).
4. **Pull forward non-jakarta bumps:** Quartz `2.3.2` → `2.5.2` (verify with the Quartz CRUD round trip in `smoke-test.sh`) and Jackson `2.12.7.1` → `2.21.x` if not already done in Phase 0.
5. **Verify:** Run full unit tests, `redeploy.sh` with JDK 21 Tomcat 8.5, `smoke-test.sh`, `crawl.py`, `rest_capture.sh`, and SOAP test. Commit Phase 1.

---

### Cutover Checkpoints (apply across Phases 2–5)
Phases 2–5 land together on one branch, but must not be one untested diff. Gate them in this order, committing on the branch at each gate:
1. **`core` compiles** on jakarta + Boot 4.2 BOM + Hibernate 7 (`mvn -pl core compile`).
2. **`core` tests green** (`mvn -pl core test -Ddb.test.name=openclinica`, then `reset-db.sh --yes`), including the ID-generation round trip (Verification §9).
3. **`web` compiles and its WAR boots** on Tomcat 11 with no `ClassNotFoundException` / `NoSuchMethodError`; login + `smoke-test.sh` page checks pass; `crawl.py` and `rest_capture.sh` diffs reviewed.
4. **Only then start `ws`** (Spring-WS 5 + WSS4J): WAR boots, 5 WSDLs served, SOAP `listAllRequest` valid/invalid password test passes.
5. Squash or keep the branch commits, then run the Phase 7 full verification before merging to `master`.

---

### Phase 2: Tomcat 11 + `javax.*` → `jakarta.*` Namespace + SiteMesh 3.3.0
> **Note:** Execute Phases 2, 3, 4, and 5 in a coordinated feature branch because Spring 7, Hibernate 7, Spring-WS 5, and Tomcat 11 all require `jakarta.*` simultaneously. Follow the Cutover Checkpoints above.

1. **Tomcat 11 setup:**
   - Install Apache Tomcat `11.0.x` in `~/opt/apache-tomcat-11.x`, configure port `8085` and systemd user unit `openclinica`, and update `scripts/upgrade-tools/redeploy.sh`.
   - You can optionally upgrade directly from **JDK 21 to JDK 25 LTS** (`/usr/lib/jvm/java-25-openjdk`, already installed) once `cglib-nodep 2.1_3` and Spring 5.3 are removed.
2. **Deployment descriptors (`web.xml`):**
   - Update `web/src/main/webapp/WEB-INF/web.xml` and `ws/src/main/webapp/WEB-INF/web.xml` from Servlet `2.4` (`http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd`) to Jakarta Servlet `6.1` (`https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd`).
   - Update Maven APIs:
     - `jakarta.servlet:jakarta.servlet-api:6.1.0` (`provided`)
     - `jakarta.servlet.jsp:jakarta.servlet.jsp-api:4.0.0` (`provided`)
     - `jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.2` + `org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1`
     - `org.eclipse.angus:angus-mail:2.0.3` (replaces `javax.mail:mail:1.4.5`)
     - `jakarta.transaction:jakarta.transaction-api:2.0.1`
     - `jakarta.annotation:jakarta.annotation-api:3.0.0`
     - `jakarta.validation:jakarta.validation-api:3.1.0`
     - `jakarta.persistence:jakarta.persistence-api:3.2.0`
     - `jakarta.xml.bind:jakarta.xml.bind-api:4.0.2` + `org.glassfish.jaxb:jaxb-runtime:4.0.5`
3. **Codebase `javax.*` → `jakarta.*` import migration (~240 Java files + 1 JSP):**
   - **Convert:** `javax.servlet.*`, `javax.persistence.*`, `javax.validation.*`, `javax.xml.bind.*`, `javax.transaction.*`, `javax.annotation.*`, `javax.mail.*`, `javax.activation.*`, `javax.jws.*`, `javax.xml.ws.*`, `javax.xml.soap.*` → `jakarta.*`.
   - **DO NOT CONVERT (JDK standard packages):** `javax.sql.DataSource`, `javax.xml.parsers.*`, `javax.xml.transform.*`, `javax.xml.namespace.*`, `javax.xml.xpath.*`, `javax.naming.*`, `javax.crypto.*`, `javax.net.*`.
   - Audit custom tag libraries (`web/src/main/webapp/WEB-INF/tlds/oc.tld`, `com.akazaresearch.tags`, `com.akazaresearch.viewtags`, and `taglibs:standard:1.1.2`) and all 821 `.jsp`/`.tag` files for removed JSTL 1.x / Servlet 2.4 constructs or `javax.servlet` references.
4. **SiteMesh `2.3` → `3.3.0` (`web/` and `ws/`):**
   - Replace `opensymphony:sitemesh:2.3` with `org.sitemesh:sitemesh:3.3.0` (supports Jakarta Servlet 6.1 / Tomcat 11).
   - Replace `com.opensymphony.module.sitemesh.filter.PageFilter` in `web.xml` with `org.sitemesh.config.ConfigurableSiteMeshFilter`.
   - Convert `WEB-INF/sitemesh.xml` / `decorators.xml` to SiteMesh 3 configuration (`sitemesh3.xml`) and replace `<decorator:head/>` / `<decorator:body/>` / `<decorator:title/>` in `decorator.jsp` with `<sitemesh:write property="head"/>`, `<sitemesh:write property="body"/>`, `<sitemesh:write property="title"/>`.
   - **Known risk:** SiteMesh 3 path-mapping and exclude semantics differ from SiteMesh 2's `decorators.xml` (`<excludes>`, pattern matching, decorator selection by meta tag). Port each mapping explicitly and diff the `crawl.py` output for pages that lost (or gained) decoration — AJAX fragments, popups, and file downloads are the usual casualties.
5. **Multipart upload library:**
   - No `MultipartResolver` is configured; `org.apache.commons.fileupload` is used directly in 4 Java files. Port those to `org.apache.commons:commons-fileupload2-jakarta-servlet6` or to native `HttpServletRequest.getParts()` (`<multipart-config>` on the servlet in `web.xml`), and remove `commons-fileupload:1.3.3` from `core`, `web`, and `ws` poms.

---

### Phase 3: Hibernate ORM `5.6.15.Final` → `7.4.17.Final` & Validator `9.x`
1. **Maven coordinates & properties:**
   - Replace `org.hibernate:hibernate-core:5.6.15.Final` with `org.hibernate.orm:hibernate-core:7.4.17.Final`.
   - Replace `org.hibernate.validator:hibernate-validator:6.2.5.Final` with `org.hibernate.validator:hibernate-validator:9.0.1.Final` (or Boot 4 managed version).
2. **Spring Hibernate integration (`applicationContext-core-hibernate.xml`):**
   - Spring 7 removed `org.springframework.orm.hibernate5`. Update `sessionFactory` and `transactionManager` to **`org.springframework.orm.jpa.hibernate.LocalSessionFactoryBean`** and **`org.springframework.orm.jpa.hibernate.HibernateTransactionManager`**.
   - Remove the `hibernateTemplate` bean and the `HibernateTemplate` usage in the only two files that use it — `AbstractDomainDao` and `CompositeIdAbstractDomainDao` — injecting `SessionFactory` directly.
3. **ID generation (highest-risk item — do this first):**
   - `applicationContext-core-hibernate.xml` sets `hibernate.id.new_generator_mappings=false`; that setting was **removed in Hibernate 6**. Delete it.
   - ~33 entities use `@GeneratedValue(generator = "id-generator")` + `@GenericGenerator(strategy = "native", parameters = @Parameter(name = "sequence", value = "<table>_<col>_seq"))`. Under Hibernate 6/7, `native` on PostgreSQL resolves to `SequenceStyleGenerator`, which reads `sequence_name` — **the legacy `sequence` parameter is ignored**, so it would fall back to a default sequence name with `allocationSize = 50`, causing insert failures or ID jumps/collisions with rows written by the legacy JDBC DAOs.
   - Convert every one to `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "id-generator")` + `@SequenceGenerator(name = "id-generator", sequenceName = "<existing seq>", allocationSize = 1)`, keeping the exact existing sequence names (`@GenericGenerator` is also deprecated).
   - Verify with the ID-generation round trip (Verification §9), not just `mapdump`.
4. **Removed Hibernate APIs:**
   - **Legacy `Criteria` (`org.hibernate.criterion.*`):** Rewrite the Hibernate `Criteria` / `DetachedCriteria` / `Restrictions` usages in the 4 affected files (`AuditUserLoginDao`, `AuditUserLoginFilter`, `AuditUserLoginSort`, `RuleActionRunLogDao`) using JPA `CriteriaBuilder` / `CriteriaQuery` or HQL (`Session.createSelectionQuery`).
   - **Removed `Session` write methods:** There are only 4 real `Session` calls, all in `AbstractDomainDao` and `CompositeIdAbstractDomainDao` (`saveOrUpdate` and `save`). Replace them with `persist(...)` (new entities, then read the ID from the entity) and `merge(...)` (detached entities, returning the managed instance). The ~98 other `.saveOrUpdate(` hits call OpenClinica's own DAO method and keep their name; only audit callers that rely on a detached instance being mutated in place by `saveOrUpdate`.
   - **Removed `Query` setter methods:** Replace `org.hibernate.Query` + `.setInteger(...)` / `.setString(...)` / `.uniqueResult()` in `AbstractDomainDao` and DAOs with typed `org.hibernate.query.Query<T>` / `MutationQuery` + `.setParameter(...)` / `.uniqueResultOptional()`.
   - **Naming Strategy:** Update `ImprovedPhysicalNamingStrategy` to Hibernate 7's `PhysicalNamingStrategy` contract (`JdbcEnvironment`).
   - **Dialect:** Hibernate 7 removed the versioned PostgreSQL dialects and auto-detects the version. Remove `hibernate.dialect` (or set it to `org.hibernate.dialect.PostgreSQLDialect`) in `datainfo.properties` filters, `core/src/test/resources/{datainfo,test}.properties`, and check the `${test.hibernateDialect}` filter value used by `ws/src/test/resources/test.properties`.
5. **Custom enum typedefs (`typedefs.hbm.xml`):**
   - Hibernate 6/7 modernized `UserType<T>` and removed old `ParameterizedType` patterns in `CodedEnumType`. Convert `CodedEnumType` to JPA `@Converter` or Hibernate 7 `UserType<T>`, or bind via `@TypeRegistration` / `@Type`.
6. **Second-level cache (`hibernate-ehcache` → Ehcache 3 / JCache):**
   - Replace `org.hibernate:hibernate-ehcache` (`SingletonEhCacheRegionFactory`) and `net.sf.ehcache:ehcache:2.10` with `org.hibernate.orm:hibernate-jcache:7.4.17.Final` + `org.ehcache:ehcache:3.10.8` (`classifier=jakarta`) + `javax.cache:cache-api`.
   - Update `core/src/main/resources/org/akaza/openclinica/ehcache.xml` to Ehcache 3 schema and refactor `EhCacheWrapper` to JCache (`javax.cache.Cache`) or Ehcache 3 API.
7. **Verify mappings:** Port `scripts/upgrade-tools/mapdump` to Hibernate 7 and verify every entity table, column, collection, and ID generator matches the Hibernate 5 baseline, then run the ID-generation round trip (Verification §9).

---

### Phase 4: Spring Boot `4.2.0` (Spring Framework `7.1.0`, Spring Security `7.2.0`) & OAuth2 Replacement
1. **Adopt Spring Boot 4.2.0 (decided):**
   - Use **`spring-boot-starter-parent:4.2.0`** as the parent POM (or import `spring-boot-dependencies:4.2.0` as a BOM if the parent slot is needed) and delete the hand-pinned `spring.version`, `spring-security.version`, `spring-ws.version`, `hibernate.version`, `jackson.version`, `postgresql.version` properties in favour of Boot-managed versions.
   - Package `web/` and `ws/` as WARs on Tomcat 11 via `SpringBootServletInitializer`, with Tomcat marked `provided` (`spring-boot-starter-tomcat` scope `provided`).
   - Keep the existing XML application contexts initially (`@ImportResource`) and disable Boot auto-configurations that would conflict with them (`DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `SecurityAutoConfiguration`, `LiquibaseAutoConfiguration`, `QuartzAutoConfiguration`) until each is deliberately migrated.

2. **Spring Framework `5.3.39` → `7.1.0`:**
   - Replace all `org.springframework.orm.hibernate5.*` references with `org.springframework.orm.jpa.hibernate.*`.
   - Review Spring MVC controllers in `web/` and `ws/` for Spring 6/7 path-matching defaults (`setUseTrailingSlashMatch(false)` is default in Spring 6+; verify `/rest`, `/rest2`, `/pages`, and legacy servlets still resolve identically via `crawl.py` and `rest_capture.sh`).
   - Remove deprecated `org.springframework.util.Base64Utils` / `ReflectionUtils` or old `HttpInvoker` / `CommonsHttpClient` references if present.
3. **Spring Security `5.8.16` → `7.2.0`:**
   - Convert `web/.../applicationContext-security.xml` and `ws/.../applicationContext-security.xml` from `use-expressions="false"` XML (removed in Spring Security 7) to **Java `@Configuration` (`@EnableWebSecurity` + `SecurityFilterChain` beans)** or SpEL-enabled XML (`use-expressions="true"` with `hasRole('USER')`).
   - Preserve exact legacy runtime behavior:
     - `LegacyDigestPasswordEncoder` (unsalted SHA-1 / MD5 hex) so stored user hashes and SOAP WS-Security tokens remain valid.
     - `OpenClinicaUsernamePasswordAuthenticationFilter`, `OpenClinicaSessionRegistryImpl` (`maximumSessions = 1`), `ConcurrentSessionFilter`, and `OpenClinicaSecurityContextLogoutHandler`.
     - Explicit `csrf(AbstractHttpConfigurer::disable)` and `headers(AbstractHttpConfigurer::disable)` initially (matching current JSPs without CSRF tokens), then plan CSRF enablement as a separate hardening task later.
     - Update `SecurityContextHolder` / `HttpSessionSecurityContextRepository` persistence for Spring Security 6/7 (`requireExplicitSave` behavior) so custom login filters continue saving the `SecurityContext` into the HTTP session.
   - Delete dead `web/src/main/webapp/WEB-INF/security-config.xml` and `ws/src/main/webapp/WEB-INF/security-config.xml`.
4. **OAuth2 (`spring-security-oauth2` `2.5.2.RELEASE`):**
   - Audit whether `/oauth/token` and `<oauth:authorization-server>` (`clientDetails` `my-trusted-client`, `my-less-trusted-client`, `designer`) are still used now that external Participate/Randomize/Designer integrations are removed.
   - **If unused:** Remove `spring-security-oauth2`, `oauth:` XML namespaces, `tokenServices`, `tokenStore` (`JdbcTokenStore`), `userApprovalHandler`, and `access_confirmation.jsp`.
   - **If local REST API clients require OAuth2 tokens:** Migrate to **`org.springframework.security:spring-security-oauth2-authorization-server`** (part of Spring Security 7 / Boot 4).

---

### Phase 5: Spring-WS `3.1.8` → `5.1.0`
1. **Upgrade coordinates:** `spring-ws-core` and `spring-ws-security` → `5.1.0`; replace `org.apache.ws.commons.schema:XmlSchema:1.4.7` with `org.apache.ws.xmlschema:xmlschema-core:2.3.1`; `wsdl4j:1.6.3` remains supported.
2. **Replace `XwsSecurityInterceptor` (`ws/.../ws-servlet-config.xml`):**
   - Spring-WS 5 removed Sun XWSS (`XwsSecurityInterceptor`). Replace it with **`org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor`** (Apache WSS4J for Jakarta), wired with a `CallbackHandler` validating `UsernameToken` plaintext/digest against OpenClinica's `LegacyDigestPasswordEncoder` stored hash format.
   - **Important:** OpenClinica SOAP clients send the user's **stored MD5 hash** as the `UsernameToken` password, not the plaintext password. The `CallbackHandler` must therefore look up the user and hand back the stored hash as the expected "password" (for `PasswordText`, compare the token value to the stored hash; for `PasswordDigest`, return the stored hash so WSS4J computes the digest from it). Keep `ws/.../UserPermissionInterceptor` (which enforces `run_webservices=true`) in the interceptor chain after the WSS4J interceptor.
3. **Replace removed endpoint mappings & adapters (`ws-servlet-config.xml`):**
   - Replace `PayloadRootAnnotationMethodEndpointMapping`, `XPathParamAnnotationMethodEndpointAdapter`, and `GenericMarshallingMethodEndpointAdapter` with `<sws:annotation-driven marshaller="jaxb2Marshaller" unmarshaller="jaxb2Marshaller"/>` (or Java `@EnableWs` configuration, `DefaultMethodEndpointAdapter`, `MarshallingPayloadMethodProcessor`, and `XPathParamMethodArgumentResolver`).
   - Verify namespace prefixes (`e`, `studySubject`, `study`, `sed`, `data`, `odm13`) and MTOM support (`mtomEnabled="true"`) on all `@Endpoint` classes.
4. **Clean up `web/src/main/webapp/WEB-INF/web.xml`:** Remove the broken `/ws/*` `MessageDispatcherServlet` mapping from `web/` (keep SOAP exclusively in `ws/`).

---

### Phase 6: Jackson 3, Liquibase 5, Quartz 2.5 & Legacy Libraries
1. **Jackson 2.21 → `3.3.0` (follow-up, not on the cutover critical path):** Spring 7 still supports Jackson 2, so ship the cutover on Jackson 2.21.x and do this afterwards as its own commit.
   - Update dependency coordinates from `com.fasterxml.jackson.core:jackson-databind` to **`tools.jackson.core:jackson-databind:3.3.0`** (keeping `jackson-annotations` `2.21` / `3.x` compatibility per Jackson 3 rules).
   - Refactor the ~13 files importing `com.fasterxml.jackson.*` (`EventCrfFlag`, `ItemDataFlag`, `IdtView`, `ODMClinicalDataController`, `EnketoURLResponse`, `pmanage` DTOs) to `tools.jackson.*` and `JsonMapper.builder()`.
   - Remove redundant `net.sf.json-lib:json-lib` (`jdk15` classifier) and `ezmorph` if not needed, or verify `json-lib` isn't touched by Jackson migration.
2. **Liquibase `4.3.5` → `4.33.x` (recommended) or `5.0.4`:**
   - Update `org.liquibase:liquibase-core` to **`4.33.x`** (final Apache-2.0 line) unless you specifically want Liquibase 5, which uses the Functional Source License.
   - Changelogs still declare `dbchangelog-1.9` XSDs; confirm the chosen version still resolves them offline (bundled XSDs) or update the headers.
   - **Checksum change:** releases after 4.3.5 changed the changeset checksum algorithm. Harmless on the always-reset local DB, but existing installations will have their `databasechangelog` checksums recomputed on first run — note this in release notes and test an upgrade against a DB populated by the old version.
   - Remove obsolete `org.liquibase:liquibase-plugin:1.9.5.0` from `pom.xml` / `core/pom.xml` and replace with `org.liquibase:liquibase-maven-plugin:5.0.4` if CLI plugin goals are used.
   - Test a clean `reset-db.sh` + application startup so every Liquibase XML changelog executes cleanly against PostgreSQL 18.
3. **Quartz:** Already bumped to `2.5.2` in Phase 1; just re-run the Quartz job round trip in `scripts/smoke-test.sh` on Spring 7.
4. **Remaining Legacy Libraries (Audit & Modernize):**
   - **RSS (`rome` `1.0`, `rome-fetcher` `1.0`):** Audit for any remaining code references now that the RSS news box has been removed; delete if unused.
   - **Test stack:** Upgrade JUnit `4.11` + `httpunit` `1.6.1` + `mockrunner` `0.4.2` → JUnit 5 (`junit-jupiter`) + Mockito `5.18+` + Spring `MockMvc`.
   - **Date/Time & Utilities:** Plan gradual cleanup of `joda-time:1.6` → `java.time`, `commons-lang:2.3` → `commons-lang3:3.17+`, `commons-collections:3.2.1` → `commons-collections4` / JDK collections, `commons-io:1.4` → `2.18+`, `commons-dbcp:1.4` → HikariCP (`com.zaxxer:HikariCP`).
   - **XML/PDF/Excel:** Test `castor` `1.2`/`1.3.x`, `saxon` `8.7`, `fop` `1.0`, `itext` `2.1.2` (OpenPDF `2.x`), `poi` `3.0.1-FINAL` (POI `5.4+`), `jxl` `2.6.10` under JDK 21/25 and upgrade/replace any that fail or trigger security findings.

---

### Phase 7: Full End-to-End Verification & Cutover
1. Run full unit & integration test suite (`mvn test -Ddb.test.name=openclinica`), then `scripts/reset-db.sh --yes`.
2. Deploy both WARs (`OpenClinica.war` and `OpenClinica-ws.war`) to Tomcat 11 via updated `scripts/upgrade-tools/redeploy.sh` and check `catalina.out` / `localhost.*.log` for zero errors or `ClassNotFoundException`/`NoSuchMethodError`.
3. Run all automated regression checks:
   - `OC_USER=root OC_PASS=12345678 scripts/smoke-test.sh`
   - `scripts/upgrade-tools/crawl.py` vs baseline
   - `scripts/upgrade-tools/rest_capture.sh` vs baseline
   - SOAP `listAllRequest` with `UsernameToken` valid + invalid password test
   - Hibernate 7 `mapdump` diff vs baseline
   - ID-generation round trip (Verification §9)
4. Perform manual UI testing with real study data (Create Study, Upload CRF `.xls`/`.xlsx`, Enroll Study Subject, Data Entry, Rule Execution, ODM/CSV/PDF Export, Quartz scheduled jobs, User Login/Logout/Session expiry).
5. Pin verified WARs to `~/opt/openclinica-stable/` and commit/tag the release.
