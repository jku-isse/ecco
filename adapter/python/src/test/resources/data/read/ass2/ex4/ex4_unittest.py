"""
Author -- Michael Widrich
Contact -- widrich@ml.jku.at
Date -- 01.10.2019

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


ex_file = 'ex4.py'
full_points = 20
points = full_points
python = sys.executable

with open(os.path.join("unittest", "unittest_inputs_outputs.pkl"), "rb") as ufh:
    all_inputs_outputs = pkl.load(ufh)
    all_inputs = all_inputs_outputs['inputs']
    all_outputs = all_inputs_outputs['outputs']

feedback = ''

for test_i, (inputs, outputs) in enumerate(zip(all_inputs, all_outputs)):
    
    comment = ''
    fcall = ''
    with open(os.devnull, 'w') as null:
        # sys.stdout = null
        try:
            from ex4 import ex4
            proper_import = True
        except Exception:
            outs = ''
            errs = traceback.format_exc()
            points -= full_points / len(all_inputs_outputs)
            proper_import = False
        finally:
            sys.stdout.flush()
            sys.stdout = sys.__stdout__
    
    if proper_import:
        with open(os.devnull, 'w') as null:
            # sys.stdout = null
            try:
                errs = ''
                fcall = f"ex4(image_array={inputs[0]}, offset={inputs[1]}, spacing={inputs[2]}))"
                returns = ex4(image_array=inputs[0], offset=inputs[1],
                              spacing=inputs[2])

                # Check if returns and outputs are of same type
                if type(returns) != type(outputs):
                    comment = f"Output should be: {type(outputs).__name__} ('{outputs}'). \n" \
                              f"          but is: {returns}"
                    points -= full_points / len(all_inputs)
                else:
                    # Check input_array output
                    if (len(returns) != 3
                            or not isinstance(returns[0], np.ndarray)
                            or returns[0].dtype != outputs[0].dtype
                            or returns[0].shape != outputs[0].shape
                            or np.any(returns[0] != outputs[0])):
                        points -= (full_points / len(all_inputs)) / 3
                        comment = f"Incorrect 'input_array'. Output should be: " \
                                f"{outputs} \n" \
                                f"but is {returns}"
                    
                    # Check known_array output
                    if (len(returns) != 3
                            or not isinstance(returns[1], np.ndarray)
                            or returns[1].dtype != outputs[1].dtype
                            or returns[1].shape != outputs[1].shape
                            or np.any(returns[1] != outputs[1])):
                        points -= (full_points / len(all_inputs)) / 3
                        comment = f"Incorrect 'known_array'. Output should be: " \
                                f"{outputs} \n" \
                                f"but is {returns}"
                    
                    # Check target_array output
                    if (len(returns) != 3
                            or not isinstance(returns[2], np.ndarray)
                            or returns[2].dtype != outputs[2].dtype
                            or returns[2].shape != outputs[2].shape
                            or np.any(returns[2] != outputs[2])):
                        points -= (full_points / len(all_inputs)) / 3
                        comment = f"Incorrect 'target_array'. Output should be: " \
                                f"{outputs} \n" \
                                f"but is {returns}"
            
            except Exception as e:
                outs = ''
                if not type(e) == type(outputs):
                    comment = f"Output should be: {type(outputs).__name__} ('{outputs}'). \n" \
                              f"          but is:\n{traceback.format_exc()}"
                    points -= full_points / len(all_inputs)
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
