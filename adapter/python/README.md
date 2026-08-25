
# ECCO Python Adapter

tested with python 3.9.13

### Requirements and Installation:

pip install py4j (0.10.9.7)

pip install libcst (0.4.9)

### The adapter is part of [jku-isse/ecco](https://github.com/jku-isse/ecco) as a git submodule:

When cloning the main ecco repository, also call `git submodule update --init` to make sure that the adapter is cloned as well.

### Attention!
When cloning this repository to Windows, the limitation of path-lengths (~260 on windows) may cause problems - the limitation setting can be changed i.e. via the Windows Registry Editor. 
