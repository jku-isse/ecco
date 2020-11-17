{
        if (elem == null) {
            throw new IllegalArgumentException("Element may not be null "
                    + "in delete");
        }

        // TODO: Hold lock for entire recursive traversal?
        synchronized (lock) {
            if (elementsToBeDeleted.contains(elem)) {
                return;
            }
            if (top == null) {
                top = elem;
            }
            elementsToBeDeleted.add((RefObject) elem);
        }
        //#if defined(LOGGING) 
        //@#$LPS-LOGGING:GranularityType:Statement
        if (LOG.isDebugEnabled()) {
            if (top == elem) {
                LOG.debug("Set top for cascade delete to " + elem);
            }
            LOG.debug("Deleting " + elem);
        }
        //#endif

        // Begin a transaction - we'll do a bunch of reads first
        // to collect a set of elements to delete - then delete them all
        modelImpl.getRepository().beginTrans(false);
        try {
            // TODO: Encountering a deleted object during
            // any part of this traversal will
            // abort the rest of the traversal.
            // We probably should do the whole traversal
            // in a single MDR transaction.
            if (elem instanceof Element) {
                getCore().deleteElement(elem);
                if (elem instanceof ModelElement) {
                    getCore().deleteModelElement(elem);
                    // no else here to make sure Classifier with
                    // its double inheritance goes ok

                    if (elem instanceof GeneralizableElement) {
                        GeneralizableElement ge = (GeneralizableElement) elem;
                        getCore().deleteGeneralizableElement(ge);
                        if (elem instanceof Stereotype) {
                            Stereotype s = (Stereotype) elem;
                            getExtensionMechanisms().deleteStereotype(s);
                        }
                    } // no else here to make sure AssociationClass goes ok

                    if (elem instanceof Parameter) {
                        getCore().deleteParameter(elem);
                    } else if (elem instanceof Constraint) {
                        getCore().deleteConstraint(elem);
                    } else if (elem instanceof Relationship) {
                        deleteRelationship((Relationship) elem);
                    } else if (elem instanceof AssociationEnd) {
                        getCore().deleteAssociationEnd(elem);
                        if (elem instanceof AssociationEndRole) {
                            getCollaborations().deleteAssociationEndRole(elem);
                        }
                    } else if (elem instanceof Comment) {
                        getCore().deleteComment(elem);
                    } else if (elem instanceof Action) {
                        deleteAction(elem);
                    } else if (elem instanceof AttributeLink) {
                        getCommonBehavior().deleteAttributeLink(elem);
                    } else if (elem instanceof Instance) {
                        deleteInstance((Instance) elem);
                    } else if (elem instanceof Stimulus) {
                        getCommonBehavior().deleteStimulus(elem);
                    } // no else to handle multiple inheritance of linkobject

                    if (elem instanceof Link) {
                        getCommonBehavior().deleteLink(elem);
                    } else if (elem instanceof LinkEnd) {
                        getCommonBehavior().deleteLinkEnd(elem);
                    } else if (elem instanceof Interaction) {
                        getCollaborations().deleteInteraction(elem);
                    } else if (elem instanceof InteractionInstanceSet) {
                        getCollaborations().deleteInteractionInstanceSet(elem);
                    } else if (elem instanceof CollaborationInstanceSet) {
                        getCollaborations()
                                .deleteCollaborationInstanceSet(elem);
                    } else if (elem instanceof Message) {
                        getCollaborations().deleteMessage(elem);
                    } else if (elem instanceof ExtensionPoint) {
                        getUseCases().deleteExtensionPoint(elem);
                    } else if (elem instanceof StateVertex) {
                        deleteStateVertex((StateVertex) elem);
                    }

                    if (elem instanceof StateMachine) {
                        getStateMachines().deleteStateMachine(elem);
                        if (elem instanceof ActivityGraph) {
                            getActivityGraphs().deleteActivityGraph(elem);
                        }
                    } else if (elem instanceof Transition) {
                        getStateMachines().deleteTransition(elem);
                    } else if (elem instanceof Guard) {
                        getStateMachines().deleteGuard(elem);
                    } else if (elem instanceof TaggedValue) {
                        getExtensionMechanisms().deleteTaggedValue(elem);
                    } else if (elem instanceof TagDefinition) {
                        getExtensionMechanisms().deleteTagDefinition(elem);
                    }
                    // else if (elem instanceof MEvent) {
                    //
                    // }
                } else if (elem instanceof PresentationElement) {
                    getCore().deletePresentationElement(elem);
                }
            } else if (elem instanceof TemplateParameter) {
                getCore().deleteTemplateParameter(elem);
            } else if (elem instanceof TemplateArgument) {
                getCore().deleteTemplateArgument(elem);
            } else if (elem instanceof ElementImport) {
                getModelManagement().deleteElementImport(elem);
            } else if (elem instanceof ElementResidence) {
                getCore().deleteElementResidence(elem);
            } 
            if (elem instanceof Partition) {
                getActivityGraphs().deletePartition(elem);
            }
            if (elem instanceof Feature) {
                deleteFeature((Feature) elem);
            } else if (elem instanceof Namespace) {
                deleteNamespace((Namespace) elem);
            }
        } catch (InvalidObjectException e) {
            // If we get this with the repository locked, it means our root
            // model element was already deleted.  Nothing to do...
            //#if defined(LOGGING) 
            //@#$LPS-LOGGING:GranularityType:Statement
            LOG.error("Encountered deleted object during delete of " + elem);
            //#endif
        } catch (InvalidElementException e) {
            // Our wrapped version of the same error
            //#if defined(LOGGING) 
            //@#$LPS-LOGGING:GranularityType:Statement
            LOG.error("Encountered deleted object during delete of " + elem);
            //#endif
        } finally {
            // end our transaction
            modelImpl.getRepository().endTrans();
        }

        synchronized (lock) {
            // Elements which will be deleted when their container is deleted
            // don't get added to the list of elements to be deleted
            // (but we still want to traverse them looking for other elements
            //  to be deleted)
            try {
                Object container = ((RefObject) elem).refImmediateComposite();
                if (container == null
                        || !elementsToBeDeleted.contains(container)
                        // There is a bug in the version of MDR (20050711) that 
                        // we use  that causes it to fail to delete aggregate 
                        // elements which are single valued and where the 
                        // aggregate end is listed second in the association
                        // defined in the metamodel. For the UML 1.4 metamodel,
                        // this affects a StateMachine's top StateVertex and
                        // a Transition's Guard.  See issue 4948 & 5227 - tfm 
                        // 20080713
                        || (container instanceof StateMachine 
                                && elem instanceof StateVertex)
                        || (container instanceof Transition 
                                && elem instanceof Guard)) {
                    elementsInDeletionOrder.add((RefObject) elem);
                }
            } catch (InvalidObjectException e) {
                //#if defined(LOGGING) 
                //@#$LPS-LOGGING:GranularityType:Statement
                //@#$LPS-LOGGING:Localization:NestedStatement
                LOG.debug("Object already deleted " + elem);
                //#endif
            }

            if (elem == top) {
                for (RefObject o : elementsInDeletionOrder) {
                    // TODO: This doesn't belong here, but it's not a good time
                    // to move it.  Find someplace less obtrusive than this
                    // inner loop. - tfm
                    if (o instanceof CompositeState) {
                        // This enforces the following well-formedness rule.
                        // <p>Well formedness rule 4.12.3.1 CompositeState
                        // [4] There have to be at least two composite
                        // substates in a concurrent composite state.<p>
                        // If this is broken by deletion of substate then we
                        // change the parent composite substate to be not
                        // concurrent.
                        CompositeState deletedCompositeState = 
                            (CompositeState) o;
                        try {
                            CompositeState containingCompositeState =
                                deletedCompositeState.getContainer();
                            if (containingCompositeState != null
                                    && containingCompositeState.
                                    isConcurrent()
                                    && containingCompositeState.getSubvertex().
                                        size() == 1) {
                                containingCompositeState.setConcurrent(false);
                            }
                        } catch (InvalidObjectException e) {
                            //#if defined(LOGGING) 
                            //@#$LPS-LOGGING:GranularityType:Statement
                            //@#$LPS-LOGGING:Localization:NestedStatement
                            LOG.debug("Object already deleted " + o);
                            //#endif
                        }
                    }
                    try {
                        o.refDelete();
                    } catch (InvalidObjectException e) {
                        //#if defined(LOGGING) 
                        //@#$LPS-LOGGING:GranularityType:Statement
                        //@#$LPS-LOGGING:Localization:NestedStatement
                        LOG.debug("Object already deleted " + o);
                        //#endif
                    }
                    elementsToBeDeleted.remove(o);
                }
                top = null;
                elementsInDeletionOrder.clear();
                if (!elementsToBeDeleted.isEmpty()) {
                    //#if defined(LOGGING) 
                    //@#$LPS-LOGGING:GranularityType:Statement
                    //@#$LPS-LOGGING:Localization:NestedStatement
                    LOG.debug("**Skipped deleting "
                            + elementsToBeDeleted.size()
                            + " elements (probably in a deleted container");
                    //#endif
                    elementsToBeDeleted.clear();
                }
            }
        }
        
        Model.execute(new DummyModelCommand());
}