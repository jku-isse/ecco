"""
Author -- Michael Widrich, Andreas Schörgenhumer
Contact -- schoergenhumer@ml.jku.at
Date -- 04.03.2022

###############################################################################

The following copyright statement applies to all code within this file.

Copyright statement:
This  material,  no  matter  whether  in  printed  or  electronic  form,
may  be  used  for personal  and non-commercial educational use only.
Any reproduction of this manuscript, no matter whether as a whole or in parts,
no matter whether in printed or in electronic form, requires explicit prior
acceptance of the authors.

###############################################################################

Images taken from: https://pixabay.com/
"""

import hashlib
import os
import shutil
import sys
import traceback
from glob import glob

import dill as pkl


def print_outs(outs, line_token="-"):
    print(line_token * 40)
    print(outs, end="" if isinstance(outs, str) and outs.endswith("\n") else "\n")
    print(line_token * 40)


ex_file = "ex2.py"
full_points = 15
points = full_points
python = sys.executable

solutions_dir = os.path.join("unittest", "solutions")
outputs_dir = os.path.join("unittest", "outputs")

# Remove previous outputs folder
shutil.rmtree(outputs_dir, ignore_errors=True)

inputs = sorted(glob(os.path.join("unittest", "unittest_input_*"), recursive=True))
if not len(inputs):
    raise FileNotFoundError("Could not find unittest_input_* files")

with open(os.path.join(solutions_dir, "counts.pkl"), "rb") as f:
    sol_counts = pkl.load(f)

for test_i, input_folder in enumerate(inputs):
    comment = ""
    fcall = ""
    
    with open(os.devnull, "w") as null:
        # sys.stdout = null
        try:
            from ex2 import validate_images
            
            proper_import = True
        except Exception:
            outs = ""
            errs = traceback.format_exc()
            points -= full_points / len(inputs)
            proper_import = False
        finally:
            sys.stdout.flush()
            sys.stdout = sys.__stdout__
    
    if proper_import:
        with open(os.devnull, "w") as null:
            # sys.stdout = null
            try:
                input_basename = os.path.basename(input_folder)
                output_dir = os.path.join(outputs_dir, input_basename)
                logfilepath = output_dir + ".log"
                formatter = "06d"
                counts = validate_images(input_dir=input_folder, output_dir=output_dir, log_file=logfilepath,
                                         formatter=formatter)
                fcall = f'validate_images(\n\tinput_dir="{input_folder}",\n\toutput_dir="{output_dir}",\n\tlog_file="{logfilepath}",\n\tformatter="{formatter}"\n)'
                errs = ""
                
                try:
                    with open(os.path.join(outputs_dir, f"{input_basename}.log"), "r") as lfh:
                        logfile = lfh.read()
                except FileNotFoundError:
                    # two cases:
                    # 1) no invalid files and thus no log file -> ok -> equal to empty tlogfile
                    # 2) invalid files but no log file -> not ok -> will fail the comparison with tlogfile (below)
                    logfile = ""
                with open(os.path.join(solutions_dir, f"{input_basename}.log"), "r") as lfh:
                    # must replace the separator that was used when creating the solution files
                    tlogfile = lfh.read().replace("\\", os.path.sep)
                
                files = sorted(glob(os.path.join(outputs_dir, input_basename, "**", "*"), recursive=True))
                hashing_function = hashlib.sha256()
                for file in files:
                    with open(file, "rb") as fh:
                        hashing_function.update(fh.read())
                hash = hashing_function.digest()
                hashing_function = hashlib.sha256()
                tfiles = sorted(glob(os.path.join(solutions_dir, input_basename, "**", "*"), recursive=True))
                for file in tfiles:
                    with open(file, "rb") as fh:
                        hashing_function.update(fh.read())
                thash = hashing_function.digest()
                
                tcounts = sol_counts[input_basename]
                
                if not counts == tcounts:
                    points -= full_points / len(inputs)
                    comment = f"Function should return {tcounts} but returned {counts}"
                elif not [f.split(os.path.sep)[-2:] for f in files] == [f.split(os.path.sep)[-2:] for f in tfiles]:
                    points -= full_points / len(inputs)
                    comment = f"Contents of output directory do not match (see directory 'solutions')"
                elif not hash == thash:
                    points -= full_points / len(inputs)
                    comment = f"Hash value of the files in the output directory do not match (see directory 'solutions')"
                elif not logfile == tlogfile:
                    points -= full_points / len(inputs)
                    comment = f"Contents of logfiles do not match (see directory 'solutions')"
            
            except Exception:
                outs = ""
                errs = traceback.format_exc()
                points -= full_points / len(inputs)
            finally:
                sys.stdout.flush()
                sys.stdout = sys.__stdout__
    
    print()
    print_outs(f"Test {test_i}", line_token="#")
    print("Function call:")
    print_outs(fcall)
    
    if errs:
        print(f"Some unexpected errors occurred:")
        print_outs(errs)
    else:
        print("Notes:")
        print_outs("No issues found" if comment == "" else comment)
    
    # due to floating point calculations it could happen that we get -0 here
    if points < 0:
        assert abs(points) < 1e-7, f"points were {points} < 0: error when subtracting points?"
        points = 0
    print(f"Current points: {points:.2f}")

print(f"\nEstimated points upon submission: {points:.2f} (out of {full_points:.2f})")
if points < full_points:
    print(f"Check the folder '{outputs_dir}' to see where your errors are")
else:
    shutil.rmtree(os.path.join(outputs_dir))
print(f"This is only an estimate, see 'Instructions for submitting homework' in Moodle "
      f"for common mistakes that can still lead to 0 points.")
