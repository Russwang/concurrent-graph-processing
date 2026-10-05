"""End-to-end CLI checks; no third-party packages required."""
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
COMMAND = ["java", "-ea", "-cp", str(ROOT / "build/classes"), "Driver"]
checks = 0


def run(args, success):
    global checks
    result = subprocess.run(COMMAND + list(map(str, args)), capture_output=True, text=True, timeout=10)
    assert (result.returncode == 0) == success, (args, result.returncode, result.stderr)
    if not success:
        assert "Error:" in result.stderr and "All done" not in result.stderr, result.stderr
    checks += 1
    return result


with tempfile.TemporaryDirectory(prefix="graph-cli-") as temporary:
    temp = Path(temporary)
    inputs = {}
    for fmt in ("COO", "CSR", "CSC"):
        file = temp / ("cycle." + fmt.lower())
        body = "1 0\n0 1\n" if fmt == "COO" else "0 1\n1 0\n"
        file.write_text(fmt + "\n2\n2\n" + body)
        inputs[fmt] = file
    for fmt in ("COO", "CSR", "CSC", "ICHOOSE"):
        for algorithm in ("PR", "CC", "OPT", "DS"):
            output = temp / "output.txt"
            result = run([algorithm, 4, output, fmt, inputs.get(fmt, inputs["CSC"])], True)
            assert result.stdout == "", result.stdout
            rows = output.read_text().splitlines()
            if algorithm == "PR":
                assert len(rows) == 2
                assert all(abs(float(row.split()[1]) - 0.5) < 1e-12 for row in rows)
            else:
                assert rows == ["0 2"], rows
    for args in ([], ["PR"], ["unknown", 1, temp / "out", "CSC", inputs["CSC"]],
                 ["PR", 0, temp / "out", "CSC", inputs["CSC"]],
                 ["PR", -1, temp / "out", "ICHOOSE", inputs["CSC"]],
                 ["PR", "abc", temp / "out", "CSC", inputs["CSC"]],
                 ["PR", 1, temp / "out", "unknown", inputs["CSC"]],
                 ["PR", 1, temp / "out", "CSC", temp / "missing.csc"],
                 ["PR", 1, temp / "missing" / "output", "CSC", inputs["CSC"]],
                 ["PR", 1, temp / "out", "CSC", inputs["COO"], inputs["CSR"]]):
        run(args, False)
    run(["pr", 2, temp / "lowercase", "csc", *inputs.values()], True)
    bad = temp / "bad.csc"
    for content in ("CSC\n2\n1\n0\n", "CSC\n1\n1\n0 nonsense\n", "CSC\n1\n0\n0\nextra\n" * 10):
        bad.write_text(content)
        (temp / "bad-output").write_text("existing output\n")
        run(["PR", 4, temp / "bad-output", "ICHOOSE", bad], False)
        assert (temp / "bad-output").read_text() == "existing output\n"
    for fmt in ("COO", "CSR", "CSC", "ICHOOSE"):
        empty = temp / "empty.txt"
        empty.write_text(("CSC" if fmt == "ICHOOSE" else fmt) + "\n0\n0\n")
        for algorithm in ("PR", "CC", "OPT", "DS"):
            run([algorithm, 4, temp / "empty-output", fmt, empty], True)
            assert (temp / "empty-output").read_text() == ""

print(f"CLI regression tests passed ({checks} cases)")
