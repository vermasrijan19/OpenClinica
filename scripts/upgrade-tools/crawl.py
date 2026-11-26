"""Breadth-first GET crawl of OpenClinica as a logged-in user; prints one line per URL.

Usage: python3 -I crawl.py BASE USER PASS [MAX_PAGES] > result.tsv
Line format: <path>\t<status>\t<flag>  (flag: ok | error-text | login-redirect)
Skips anything that looks state-changing.
"""
import html
import http.cookiejar
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

base, user, pw = sys.argv[1], sys.argv[2], sys.argv[3]
limit = int(sys.argv[4]) if len(sys.argv) > 4 else 400
web = base.rstrip("/") + "/OpenClinica"

SKIP = re.compile(
    r"(delete|remove|restore|unlock|lock|logout|signout|j_spring|del=|reset|archive|freeze|"
    r"signature|sign|unassign|import|upload|run|execute|submit|save|update|create|confirm|"
    r"migrate|purge|cancel|pause|resume|approve|reassign|change|enable|disable|close|mark|"
    r"\.(css|js|gif|png|jpg|ico|pdf|zip|xml|xls|txt)(\?|$))",
    re.I,
)

jar = http.cookiejar.CookieJar()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    pass


opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def get(url, data=None):
    try:
        r = opener.open(url, data=data, timeout=30)
        return r.status, r.geturl(), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, url, e.read().decode("utf-8", "replace")
    except Exception as e:  # timeouts etc.
        return 0, url, str(e)


get(web + "/pages/login/login")
get(web + "/j_spring_security_check", urllib.parse.urlencode({"j_username": user, "j_password": pw}).encode())

seen = set()
queue = ["/MainMenu", "/ListUserAccounts", "/ViewStudy", "/ListStudySubjects", "/ListCRF", "/ListStudy",
         "/ListSubject", "/ViewNotes?module=submit", "/ExtractDatasetsMain", "/ViewDatasets", "/ViewJob",
         "/ViewImportJob", "/ListEventDefinition", "/ListSubjectGroupClass", "/ViewRuleAssignment", "/pages/studymodule",
         "/StudyAuditLog", "/AuditUserActivity", "/ListSite", "/ViewStudyEvents", "/ChangeStudy",
         "/pages/listCurrentScheduledJobs", "/ViewCRF?crfId=1", "/UpdateProfile"]
count = 0
while queue and count < limit:
    path = queue.pop(0)
    key = re.sub(r"jsessionid=[^&?;]*", "", path)
    if key in seen:
        continue
    seen.add(key)
    count += 1
    status, final, body = get(web + path)
    if "/pages/login/login" in final:
        flag = "login-redirect"
    elif re.search(r"HTTP Status 500|Exception|stack trace|An error has occurred", body, re.I):
        flag = "error-text"
    else:
        flag = "ok"
    print("%s\t%s\t%s" % (key, status, flag), flush=True)
    if status != 200:
        continue
    for href in re.findall(r'(?:href|action)\s*=\s*["\']([^"\'#]+)', body):
        href = html.unescape(href).strip()
        if href.startswith("javascript:") or href.startswith("mailto:"):
            continue
        u = urllib.parse.urljoin(final, href)
        p = urllib.parse.urlparse(u)
        if p.netloc != urllib.parse.urlparse(web).netloc or not p.path.startswith("/OpenClinica/"):
            continue
        rel = p.path[len("/OpenClinica"):] + ("?" + p.query if p.query else "")
        rel = re.sub(r";jsessionid=[^?]*", "", rel)
        if SKIP.search(rel):
            continue
        if re.sub(r"jsessionid=[^&?;]*", "", rel) not in seen:
            queue.append(rel)
