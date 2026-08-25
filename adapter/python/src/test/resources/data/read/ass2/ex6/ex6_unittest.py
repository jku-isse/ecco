"""
Author -- Michael Widrich
Contact -- widrich@ml.jku.at
Date -- 01.10.2019

update: Van Quoc Phuong Huynh -- 20.04.2022

###############################################################################

The following copyright statement applies to all code within this file.

Copyright statement:
This  material,  no  matter  whether  in  printed  or  electronic  form,
may  be  used  for personal  and non-commercial educational use only.
Any reproduction of this manuscript, no matter whether as a whole or in parts,
no matter whether in printed or in electronic form, requires explicit prior
acceptance of the authors.

###############################################################################

"""

import os
import sys
import traceback

import dill as pkl
import numpy as np


def print_outs(outs, line_token="-"):
    print(line_token * 40)
    print(outs, end="" if isinstance(outs, str) and outs.endswith("\n") else "\n")
    print(line_token * 40)


ex_file = 'ex6.py'
full_points = 10
points = full_points
python = sys.executable

with open(os.path.join("unittest", "unittest_ex6_data.pkl"), "rb") as ufh:
    all_inputs_outputs = pkl.load(ufh)

with open(ex_file, 'r') as efh:
    efc = efh.read()
    if efc.find("sklearn") != -1:
        print("Found name of sklearn package in submission file."
              "Please remove the name of the sklearn package,"
              "even if not imported, to receive points.")
        print(f"\nEstimate points upon submission: 0 (also see checklist in moodle).")
        exit()

feedback = ''

for test_i, (inputs, outputs) in enumerate(all_inputs_outputs):
    
    comment = ''
    fcall = ''
    with open(os.devnull, 'w') as null:
        # sys.stdout = null
        try:
            from ex6 import ex6
            proper_import = True
        except Exception:
            outs = ''
            errs = traceback.format_exc()
            points -= (full_points / len(all_inputs_outputs) + 4 * (test_i < 3))
            proper_import = False
        finally:
            sys.stdout.flush()
            sys.stdout = sys.__stdout__
    
    if proper_import:
        with open(os.devnull, 'w') as null:
            # sys.stdout = null
            try:
                errs = ''
                fcall = f"ex6(logits={inputs[0]}, activation_function={inputs[1]}, threshold={inputs[2]}, targets={inputs[3]}))"
                returns = ex6(logits=inputs[0], activation_function=inputs[1], threshold=inputs[2], targets=inputs[3])
                
                if len(returns) != len(outputs):
                    points -= (full_points / len(all_inputs_outputs))
                    comment = f"Output should be: " \
                              f"{outputs} \n" \
                              f"but is {returns}"

                # Check for data type
                for return_val, output_val in zip(returns, outputs):
                    if type(return_val) != type(output_val):
                        points -= (full_points / len(all_inputs_outputs))
                        comment = f"Output should be: " \
                                  f"{outputs} \n" \
                                  f"but is {returns} " \
                                  f"(outputs should have the same datatype)"
                        break

                # Check for the confusion matrix
                if returns[0] != outputs[0]:
                    points -= (full_points / len(all_inputs_outputs))
                    comment = f"Confusion matrix should be: " \
                              f"{outputs[0]} \n" \
                              f"but is {returns[0]} "

                # Check for the remaining values
                if not all([np.isclose(r, o, atol=0, rtol=1e-6) for r, o in zip(returns[1:], outputs[1:])]):
                    points -= (full_points / len(all_inputs_outputs))
                    comment = f"Scores should be: " \
                              f"{outputs[1:]} \n" \
                              f"but are {returns[1:]} "
            
            except Exception as e:
                outs = ''
                if not type(e) == type(outputs):
                    comment = f"Output should be: {type(outputs).__name__} ('{outputs}'). \n" \
                              f"          but is:\n{traceback.format_exc()}"
                    points -= full_points / len(all_inputs_outputs)
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
print(f"This is only an estimate, see 'Instructions for submitting homework' in Moodle "
      f"for common mistakes that can still lead to 0 points.")
