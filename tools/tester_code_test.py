"""Tests for tools/tester_code.py (standard library only):  python -I tools/tester_code_test.py

Fake data only. The Kotlin side reads app/src/test/resources/update/tester_code_vectors.txt, which this test keeps in step with the tool.
"""
import io
import sys
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import tester_code as tc  # noqa: E402

VECTOR_FILE = Path(__file__).resolve().parent.parent / "app" / "src" / "test" / "resources" / "update" / "tester_code_vectors.txt"


def run(args):
    out, err = io.StringIO(), io.StringIO()
    with redirect_stdout(out), redirect_stderr(err):
        code = tc.main(args)
    return code, out.getvalue(), err.getvalue()


class TesterCodeTest(unittest.TestCase):
    def test_round_trip(self):
        code = tc.make_code("gitea", "https://git.example.org/owner/repo", "tok123", True)
        self.assertTrue(code.startswith("WFT1-"))
        info = tc.read_code(code)
        self.assertEqual("gitea", info["kind"])
        self.assertEqual("https://git.example.org/owner/repo", info["url"])
        self.assertEqual("tok123", info["token"])
        self.assertTrue(info["prerelease"])

    def test_address_and_token_are_not_readable_in_the_code(self):
        code = tc.make_code("github", "https://github.com/owner/repo", "secretvalue", False)
        self.assertNotIn("github", code.lower())
        self.assertNotIn("secretvalue", code)
        self.assertNotIn("owner", code)

    def test_no_token_is_allowed(self):
        info = tc.read_code(tc.make_code("gitea", "https://git.example.org/o/r", "", False))
        self.assertEqual("", info["token"])
        self.assertFalse(info["prerelease"])

    def test_every_single_typing_error_is_caught(self):
        code = tc.make_code("gitea", "https://git.example.org/owner/repo", "tok123", True)
        for i in range(len(tc.PREFIX), len(code)):
            for repl in "AZaz09-_":
                if code[i] == repl:
                    continue
                broken = code[:i] + repl + code[i + 1:]
                with self.assertRaises(tc.CodeError, msg="position %d -> %s" % (i, repl)):
                    tc.read_code(broken)

    def test_whitespace_from_copying_is_ignored(self):
        code = tc.make_code("gitea", "https://git.example.org/owner/repo", "tok123", False)
        wrapped = "  " + code[:30] + "\n" + code[30:] + " \r\n"
        self.assertEqual("tok123", tc.read_code(wrapped)["token"])

    def test_wrong_prefix_and_garbage(self):
        for bad in ("", "hello", "WFT2-abcdefghijkl", "WFT1-", "WFT1-abc"):
            with self.assertRaises(tc.CodeError):
                tc.read_code(bad)

    def test_address_rules(self):
        for bad in ("http://git.example.org/o/r", "https://git.example.org/o", "https://git.example.org/o/r/x",
                    "https://u:p" + chr(64) + "git.example.org/o/r", "https://git.example.org/o/r?x=1", "git.example.org/o/r"):
            with self.assertRaises(tc.CodeError, msg=bad):
                tc.make_code("gitea", bad, "t", False)
        self.assertEqual("https://git.example.org/o/r", tc.check_repo_url("HTTPS://Git.Example.org/o/r.git/"))
        with self.assertRaises(tc.CodeError):
            tc.make_code("svn", "https://git.example.org/o/r", "t", False)
        with self.assertRaises(tc.CodeError):
            tc.make_code("gitea", "https://git.example.org/o/r", "a b", False)

    def test_check_never_prints_the_token(self):
        code = tc.make_code("gitea", "https://git.example.org/owner/repo", "tok-very-secret", False)
        status, out, err = run(["check", code])
        self.assertEqual(0, status)
        self.assertNotIn("tok-very-secret", out + err)
        self.assertIn("set (15 characters)", out)
        status, out, err = run(["check", code[:-1] + ("A" if code[-1] != "A" else "B")])
        self.assertEqual(1, status)
        self.assertNotIn("tok-very-secret", out + err)

    def test_make_reads_token_from_file(self):
        import tempfile
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "t.txt"
            f.write_text("tok-from-file\n", encoding="utf-8")
            status, out, err = run(["make", "--kind", "gitea", "--url", "https://git.example.org/o/r", "--token-file", str(f)])
        self.assertEqual(0, status)
        self.assertEqual("tok-from-file", tc.read_code(out)["token"])

    def test_vector_file_is_up_to_date(self):
        self.assertTrue(VECTOR_FILE.is_file(), "run: python tools/tester_code.py vectors > " + str(VECTOR_FILE))
        self.assertEqual(tc.vectors(), VECTOR_FILE.read_text(encoding="utf-8").replace("\r\n", "\n"))
        for line in VECTOR_FILE.read_text(encoding="utf-8").splitlines():
            if line.startswith("#") or not line.strip():
                continue
            code, kind, url, token, pre = line.split("|")
            info = tc.read_code(code)
            self.assertEqual((kind, url, pre == "1"), (info["kind"], info["url"], info["prerelease"]))
            self.assertEqual(token, info["token"])


if __name__ == "__main__":
    unittest.main()
