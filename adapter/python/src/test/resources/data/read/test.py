# This is a sample Python script.
import ast
# Press Umschalt+F10 to execute it or replace it with your code.
# Press Double Shift to search everywhere for classes, files, tool windows, actions, and settings.

const = 145

def print_hi(name):

    # Use a breakpoint in the code line below to debug your script.
    print(f'Hi, {name}')  # Press Strg+F8 to toggle the breakpoint.
    f = open("test.py", "r", -1, "UTF-8")
    s = f.read()
    f.close()

    code = ast.parse(s)
    print(ast.dump(code))




class TestClass():

    def __init__(self):
        x = 5



# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    print_hi('PyCharm')

# See PyCharm help at https://www.jetbrains.com/help/pycharm/
