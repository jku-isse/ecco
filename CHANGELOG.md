
# ECCO CHANGELOG


## 0.1.9
  * upgrade to Java 13
  * upgrade to Gradle 6
  * added GitHub workflows


## 0.1.8


## 0.1.7
  * major refactoring and cleanup
  * reimplementation of presence condition computation


## 0.1.6
  * removed obsolete and unused code
  * added ci/cd
  * improved build scripts
  * refactoring of project structure
  * ...


## 0.1.5
  * major refactorings
  	* separation of service and repository (types as well as projects)
  	* separations of interfaces into public and private
  * added fetch, fork, push, pull operations
  * added simple server functionality
  * (in progress) unit and integration tests
  * ...


## 0.1.4
  * removed ordered node
  * unified all children and ordered children in a single list
  * change unique children from list in parent to boolean in child
  * moved several concepts from node to artifact to remove redundancies
  * added release history (this file)
  * added dispatch for folders
  * made gui improvements
    * feature detail view: description/edit/search
    * commit/checkout result detail view
    * (in progress) artifact detail view
    * (in progress) dependency graph view
    * (in progress) association detail view: containment table, modules table
    * (in progress) artifact graph view: group children where count above limit separately by association and not in parent
    * ...
  * added option to use references in artifacts for equals
  * added requirement for artifact data to be serializable
  * added transactions to data layer
  * (in progress) added jpa data backend plugin
  * moved memory (base) implementation into its own data plugin
  * added order selector to composition


## 0.1.3
  * first running version

