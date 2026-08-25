import json
import pickle
import sys
import traceback

from libcst import *
from py4j.java_gateway import JavaGateway

from timeit import default_timer as timer


def parsePython(java_node: object) -> CSTNode:
    artifactBytes = java_node.getTypeArtifactBytes()
    cst_current_node = pickle.loads(artifactBytes)

    fields = {}
    java_field_nodes = java_node.getChildren()
    for fieldIdx in range(len(java_field_nodes)):
        java_field_node = java_field_nodes[fieldIdx]
        cst_attribute_name = java_field_node.getFieldArtifactName()

        java_child_nodes = java_field_node.getChildren()

        attributes = None
        for childIdx in range(len(java_child_nodes)):
            java_child = java_child_nodes[childIdx]
            cst_child_node = parsePython(java_child)

            try:
                if attributes is not None:
                    attributes = (*attributes, cst_child_node)
                else:
                    if java_field_node.isOrdered():
                        attributes = (cst_child_node,)
                    else:
                        attributes = cst_child_node
            except Exception:
                # for debugging
                logger.info(cst_attribute_name)
                logger.severe(traceback.format_exc)

        if attributes is not None:
            if java_field_node.getParentFieldName() is not None:
                cst_node_to_add_field = getattr(cst_current_node, java_field_node.getParentFieldName())
                cst_node_to_add_field = cst_node_to_add_field.with_changes(**{cst_attribute_name: attributes})
                pars = {java_field_node.getParentFieldName(): cst_node_to_add_field}
                # fields.update({java_field_node.getParentFieldName(): cst_node_to_add_field})
                cst_current_node = cst_current_node.with_changes(**pars)
            else:
                # add to dict for later update
                fields.update({cst_attribute_name: attributes})

    return cst_current_node.with_changes(**fields)


def parseJsonOrJupyter(java_node: object):
    if java_node.isJsonObject():
        json_node = {}

        java_field_nodes = java_node.getChildren()
        for fieldIdx in range(len(java_field_nodes)):
            java_field_node = java_field_nodes[fieldIdx]
            key = java_field_node.getJsonFieldName()
            java_field_node_child = java_field_node.getChildren()
            if len(java_field_node_child) > 0:
                java_value_node = java_field_node_child[0]
                value = parseJsonOrJupyter(java_value_node)
            else:  # error case i.e. in jupyter meta nodes (if files are inconsistent)
                value = None

            json_node.update({key: value})
        return json_node

    elif java_node.isJsonArray():
        json_node = []

        java_field_nodes = java_node.getChildren()
        for fieldIdx in range(len(java_field_nodes)):
            java_value_node = java_field_nodes[fieldIdx]
            value = parseJsonOrJupyter(java_value_node)
            json_node.append(value)
        return json_node

    elif java_node.isJsonString():
        return java_node.getJsonString()
    elif java_node.isJsonBoolean():
        return java_node.getJsonBoolean()
    elif java_node.isJsonInteger():
        return java_node.getJsonInteger()
    elif java_node.isJsonRealNumber():
        return java_node.getJsonRealNumber()
    elif java_node.isJsonNullValue():
        return None
    if java_node.isJupyterCell():
        return parseJupyterCellNode(java_node)
    else:
        raise Exception("Unexpected type of Json-Artifact received")


def parseJupyterCellNode(java_cell_node: object):
    if java_cell_node.getCellType() == "markdown":
        lines = java_cell_node.getChildren()
        source = []
        for lineIdx in range(len(lines)):
            line_node = lines[lineIdx]
            source.append(line_node.getLine())

        return ({
            "cell_type": java_cell_node.getCellType(),
            "source": source,
            "metadata": {
                "collapsed": False
            }
        })

    elif java_cell_node.getCellType() == "code":
        if java_cell_node.getParseType() == "markdown":

            lines = java_cell_node.getChildren()
            source = []
            for lineIdx in range(len(lines)):
                line_node = lines[lineIdx]
                source.append(line_node.getLine())

            return ({
                "cell_type": java_cell_node.getCellType(),
                "execution_count": None,
                "outputs": [],
                "source": source,
                "metadata": {
                    "collapsed": False
                }
            })
        elif java_cell_node.getParseType() == "code":
            if len(java_cell_node.getChildren()) != 1:
                raise Exception("Expected 1 module node, found: " + str(len(java_cell_node.getChildren())))
            else:
                source = parsePython(java_cell_node.getChildren()[0])

                return ({
                    "cell_type": "code",
                    "execution_count": None,
                    "outputs": [],
                    "source": source.code.splitlines(True),
                    "metadata": {
                        "collapsed": False
                    }
                })
        else:
            raise Exception("Unknown parse type:" + str(java_cell_node.getParseType()))
    else:
        raise Exception("Unknown cell type:" + str(java_cell_node.getCellType()))


def write(filename: str):
    logger.info(f"PY: Starting Writer Script for {filename}")

    gateway = JavaGateway()
    ep = gateway.entry_point

    # parse code from Java Artifact Tree
    root = ep.getRoot()
    if filename.endswith("py"):
        start = timer()
        parsed = parsePython(root)
        logger.info("PY: Successfully traversed and parsed to LibCST-Graph (%4.3fms)" % ((timer() - start) * 1000))

        start = timer()
        code = parsed.code
        logger.info("PY: Successfully parsed to Python code (%4.3fms)" % ((timer() - start) * 1000))
    elif filename.endswith("json"):
        json_dict = parseJsonOrJupyter(root)
        code = json.dumps(json_dict, indent=4)
    elif filename.endswith("ipynb"):
        json_dict = parseJsonOrJupyter(root)
        code = json.dumps(json_dict, indent=4)
    else:
        raise Exception(f"Error! Trying to create file with unknown file extension ({filename})")

    start = timer()
    f = open(filename, "w", -1, "UTF-8")  # open file
    f.write(code)
    f.close()  # close file
    logger.info("PY: Successfully wrote file (%4.3fms)" % ((timer() - start) * 1000))


if __name__ == '__main__':
    main_start = timer()
    logger = JavaGateway().entry_point.getLogger()
    try:
        write(sys.argv[1])
    except Exception as e:
        logger.severe(traceback.format_exc())
        exit(1)

    logger.info("PY: Successfully finished Writer-Script (%4.3fms total)" % ((timer() - main_start) * 1000))
