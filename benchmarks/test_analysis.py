"""Synthetic analyzer tests, never physical campaign evidence."""
import copy
import json
from pathlib import Path
import unittest
from analyze import analyze
from schedule import schedule


def sample(trial=1, value=10):
    return dict(session=1, trial=trial, framework='native', treatment='broker',
                operation='generated-sha256-v1', bytes=1024, process_state='resident',
                cache_state='uncontrolled', metric='end_to_end_ms', value=value,
                status='ok', device_class='physical')


class AnalysisTests(unittest.TestCase):
    def test_empty_is_not_a_campaign(self):
        with self.assertRaises(ValueError): analyze([])

    def test_duplicate_is_rejected(self):
        with self.assertRaises(ValueError): analyze([sample(), sample()])

    def test_failed_and_excluded_preserved_without_imputation(self):
        failed = sample(2, None); failed.update(status='failed', reason='deadline')
        excluded = sample(3, None); excluded.update(status='excluded', reason='thermal')
        result = analyze([sample(), failed, excluded])['groups'][0]
        self.assertEqual((result['passed'], result['failed'], result['excluded'], result['median']), (1,1,1,10))
        self.assertIsNone(result['p95_nearest_rank'])

    def test_tail_requires_twenty_samples(self):
        result = analyze([sample(n,n) for n in range(1,21)])['groups'][0]
        self.assertEqual(result['p95_nearest_rank'],19)
        self.assertEqual(result['median'],10.5)

    def test_invalid_numeric_values_rejected(self):
        for value in [None, True, -1, float('nan'), float('inf')]:
            with self.subTest(value=value), self.assertRaises(ValueError): analyze([sample(value=value)])

    def test_emulator_not_physical_evidence(self):
        row = sample(); row['device_class']='emulator'
        with self.assertRaises(ValueError): analyze([row])

    def test_unsupported_or_unattributed_rows_rejected(self):
        for key,value in [('status','timeout'),('bytes',134217729),('bytes',True),('session',0),('operation','other')]:
            row=sample(); row[key]=value
            with self.subTest(key=key), self.assertRaises(ValueError): analyze([row])

    def test_sessions_and_storage_profiles_never_pooled(self):
        other=sample(); other['session']=2
        file=sample(); file['operation']='regular-file-sha256-v1'
        self.assertEqual(len(analyze([sample(),other,file])['groups']),3)

    def test_schedule_is_balanced_and_deterministic(self):
        plan=json.loads((Path(__file__).parent/'experiment-v1.json').read_text())
        result=schedule(plan)
        self.assertEqual(result,schedule(copy.deepcopy(plan)))
        self.assertFalse(result['executed'])
        rows=result['interactions']; self.assertEqual(len(rows),720)
        self.assertEqual(len(result['launches']),360)
        self.assertTrue(all(x['treatment']=='no_work' for x in result['launches']))
        for session in range(1,4):
            for block in range(1,31):
                cells={(x['framework'],x['treatment'],x['process_state']) for x in rows if x['session']==session and x['block']==block}
                self.assertEqual(len(cells),8)


if __name__ == '__main__': unittest.main()
