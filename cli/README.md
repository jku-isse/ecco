
# ECCO CLI

Command Line Interface (CLI) for ECCO.

```
usage: ecco [-h] [-v] [-r REPODIR] [-b BASEDIR] COMMAND ...

ECCO. A Variability-Aware / Feature-Oriented Version Control System.

optional arguments:
  -h, --help             show this help message and exit
  -v, --version          show the version
  -r REPODIR, --repodir REPODIR
						 set the repository directory to use
  -b BASEDIR, --basedir BASEDIR
						 set the base directory to use

COMMANDs:
  List of valid commands.

  COMMAND                DESCRIPTION
	init                 initialize a new repository
	status               status of repository
	get                  get the value of a property
	set                  set the value of a property
	checkout             checkout a configuration
	commit               commit a configuration
	fork                 fork from another repository
	pull                 pull from a remote
	push                 push to a remote
	fetch                fetch from a remote
	remotes              manage remotes
	features             manage features
	traces               manage traces
	dg (dependencyGraph)
						 dependency graph
```

