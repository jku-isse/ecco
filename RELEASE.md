
# ecco release history



* 0.1.5
  * major refactoring, most notably separation of service and repository
  * added fetch, fork, push, pull commands
  * added simple server functionality
  * (in progress) unit and integration tests
  * ...



* 0.1.4
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



* 0.1.3
  * first running version


