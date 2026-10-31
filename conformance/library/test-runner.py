"""Parser-only synthetic fixtures. These do not execute or prove Android behavior."""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location('runner', pathlib.Path(__file__).with_name('run-android.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def receipt(code=0, count=1, terminal=True):
    start = f'INSTRUMENTATION_STATUS: class=fixture.Test\nINSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS: numtests={count}\nINSTRUMENTATION_STATUS_CODE: 1\n'
    end = f'INSTRUMENTATION_STATUS: class=fixture.Test\nINSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS: numtests={count}\nINSTRUMENTATION_STATUS_CODE: {code}\n' if terminal else ''
    return start + end + f'OK ({count} tests)\nINSTRUMENTATION_CODE: -1\n'


class ParserOnlyTest(unittest.TestCase):
    def check(self, text, exit_code=0, timeout=False):
        return runner.parse_result(text, exit_code, timeout, 1, 'fixture.Test#works')

    def test_complete_positive(self):
        self.assertTrue(self.check(receipt())['passed'])

    def test_zero_tests_rejected(self):
        self.assertFalse(self.check('OK (0 tests)\nINSTRUMENTATION_CODE: -1\n')['passed'])

    def test_nonzero_adb_exit_rejected_even_with_ok_text(self):
        self.assertFalse(self.check(receipt(), exit_code=1)['passed'])

    def test_assumption_and_ignore_are_skips_never_passes(self):
        for code in (-3, -4):
            result = self.check(receipt(code))
            self.assertFalse(result['passed'])
            self.assertEqual(1, result['tests_skipped'])
            self.assertEqual(0, result['tests_failed'])

    def test_missing_terminal_rejected(self):
        self.assertFalse(self.check(receipt(terminal=False))['passed'])

    def test_missing_completion_marker_rejected(self):
        self.assertFalse(self.check(receipt().replace('INSTRUMENTATION_CODE: -1', ''))['passed'])

    def test_duplicate_terminal_rejected(self):
        self.assertFalse(self.check(receipt() + receipt())['passed'])

    def test_timeout_rejected_even_with_complete_output(self):
        self.assertFalse(self.check(receipt(), timeout=True)['passed'])

    def test_wrong_selected_identity_rejected(self):
        self.assertFalse(self.check(receipt().replace('test=works', 'test=other'))['passed'])


if __name__ == '__main__':
    unittest.main()
