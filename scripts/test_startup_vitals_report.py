import unittest
import json
import subprocess
import sys
import tempfile
from pathlib import Path
from startup_vitals_report import CLASS, summarize


def report(mode="COLD", runs=None, count=3):
    return {"benchmarks": [{"className": CLASS, "params": {"mode": mode},
                            "repeatIterations": count,
                            "metrics": {"timeToInitialDisplayMs": {"runs": runs if runs is not None else [4999, 5000, 5001]}}}]}


class StartupReportTest(unittest.TestCase):
    def test_cli_saves_evidence_and_fails_incomplete_runs(self):
        script = Path(__file__).with_name("startup_vitals_report.py")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); raw = root / "raw.json"; output = root / "summary.json"; csv = root / "summary.csv"
            raw.write_text(json.dumps(report()))
            command = [sys.executable, str(script), str(raw), "--package", "com.example.app", "--version", "build456",
                       "--output", str(output), "--csv", str(csv)]
            run = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(run.returncode, 2, run.stderr)
            self.assertEqual(json.loads(output.read_text())["targetPackage"], "com.example.app")
            self.assertIn("androidx-macrobenchmark-perfetto-ttid", csv.read_text())
            all_modes = {"benchmarks": sum([report(mode)["benchmarks"] for mode in ["COLD", "WARM", "HOT"]], [])}
            raw.write_text(json.dumps(all_modes))
            run = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(run.returncode, 0, run.stderr)
            raw.write_text('{"benchmarks": []}')
            output.unlink()
            run = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(run.returncode, 0)
            self.assertFalse(output.exists())

    def test_strict_boundary_and_real_denominator(self):
        result = summarize(report())["results"][0]
        self.assertEqual((result["slow"], result["measured"], result["missing"]), (1, 3, 0))
        self.assertAlmostEqual(result["slowPercent"], 100/3)

    def test_missing_never_becomes_fast_or_zero(self):
        result = summarize(report(runs=[6000, None], count=3))["results"][0]
        self.assertEqual((result["measured"], result["missing"], result["slowPercent"]), (1, 2, 100))

    def test_missing_metric_and_unrun_modes(self):
        data = report(runs=[])
        data["benchmarks"][0]["metrics"] = {}
        result = summarize(data)
        self.assertIsNone(result["results"][0]["slowPercent"])
        self.assertEqual(result["unrunModes"], ["WARM", "HOT"])

    def test_all_modes_and_fractional_times(self):
        data = {"benchmarks": []}
        for mode, threshold in [("COLD", 5000), ("WARM", 2000), ("HOT", 1500)]:
            data["benchmarks"] += report(mode, [threshold, threshold+.01, 0])["benchmarks"]
        result = summarize(data)
        self.assertEqual(result["unrunModes"], [])
        self.assertEqual([r["slow"] for r in result["results"]], [1, 1, 1])

    def test_refuses_bad_or_ambiguous_data(self):
        for value in [-1, float("nan"), float("inf"), True, "5001"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                summarize(report(runs=[value]))
        with self.assertRaises(ValueError):
            summarize({"benchmarks": []})
        data = report(); data["benchmarks"] *= 2
        with self.assertRaises(ValueError):
            summarize(data)
        with self.assertRaises(ValueError):
            summarize(report(runs=[1, 2, 3, 4], count=3))


if __name__ == "__main__":
    unittest.main()
