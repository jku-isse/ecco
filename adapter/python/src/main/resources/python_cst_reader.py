import inspect
import json
import pickle
import sys
import traceback
from libcst import *
from py4j.java_gateway import JavaGateway
from timeit import default_timer as timer
from typing import Union

# no visiting, but dumped entirely
dumpNodes = (EmptyLine, BaseExpression)
# dumpNodes = ()

# no visiting, no dumping, saved with parent dump
# these nodes need to be kept with their parent as identifier (i.e. Name)
skipNodes = (ImportAlias, AssignTarget, ImportFrom, NameItem, WithItem, MaybeSentinel)


def visit_required(node, attribute):
    codeString = inspect.getsource(node._visit_and_replace_children)
    for line in codeString.splitlines():
        if line.__contains__(attribute):
            return line.__contains__(attribute + "=visit_required")
    return False


class CSTReader(CSTTransformer):
    def __init__(self, parentNode):
        super().__init__()
        self.currentField = None
        self.currentNode = parentNode

        self.stack = []
        self.parentFieldStack = []
        self.visitReq = [False]

    def on_visit(self, node: CSTNode) -> bool:

        if isinstance(node, BaseSuite):
            # can not remove, but further visiting needed (keep node with parent, add attributes with parent-field info)
            return True

        if self.visitReq[-1]:
            # skip node, it can not be removed
            return False

        if isinstance(node, skipNodes):
            return False

        if isinstance(node, dumpNodes):
            self.stack.append(self.currentNode)
            self.currentNode = self.currentNode.addTypeNode()
            return False

        self.stack.append(self.currentNode)
        self.currentNode = self.currentNode.addTypeNode()
        return True

    def on_leave(self, original_node: CSTNodeT, updated_node: CSTNodeT) -> Union[
        CSTNodeT, RemovalSentinel, FlattenSentinel[CSTNodeT]]:

        if isinstance(original_node, (Try, TryStar)):
            # replace handlers with dummy-handler, because they couldn't be removed yet due to libcst-validation
            if len(original_node.handlers) > 0:
                handler = ExceptHandler(body=IndentedBlock([])) if \
                    isinstance(original_node, Try) else ExceptStarHandler(body=IndentedBlock([]))
                updated_node = updated_node.with_changes(handlers=[handler], finalbody=None)
            else:
                updated_node = updated_node.with_changes(handlers=(), finalbody=Finally(body=IndentedBlock([])))

        if isinstance(original_node, (ExceptHandler, ExceptStarHandler, Finally)):
            # create artifact, but do not remove (last one can not be removed because of validity)
            # removing happens in parent node (Try, TryStar)
            self.currentNode.setBytes(pickle.dumps(updated_node))
            self.currentNode = self.stack.pop()
            # return original node, because field 'type' in updated_node has been removed while visiting
            # can cause CSTValidationError("The bare except: handler must be the last one.") otherwise
            return original_node

        if isinstance(original_node, BaseSuite):
            # can not remove, but further visiting needed (keep node with parent, add attributes with parent-field info)
            return updated_node

        if self.visitReq[-1]:
            # skip node, it can not be removed
            return updated_node

        if isinstance(original_node, skipNodes):
            return updated_node

        if isinstance(original_node, dumpNodes):
            if isinstance(original_node, EmptyLine):
                if updated_node.comment is None:
                    updated_node = updated_node.with_changes(indent=True)

            self.currentNode.setBytes(pickle.dumps(updated_node))
            self.currentNode = self.stack.pop()
            return RemovalSentinel.REMOVE

        self.currentNode.setBytes(pickle.dumps(updated_node))
        self.currentNode = self.stack.pop()
        return RemovalSentinel.REMOVE

    def on_visit_attribute(self, node: CSTNode, attribute: str) -> None:
        if getattr(node, attribute) is None:  # ignore empty attributes
            return

        if isinstance(getattr(node, attribute), (list, tuple)) and len(getattr(node, attribute)) == 0:
            return

        if isinstance(getattr(node, attribute), BaseSuite):
            self.parentFieldStack.append(attribute)
            return

        # check if attribute is removable
        if visit_required(node, attribute):
            self.visitReq.append(True)  # no attribute node needed
        else:
            self.visitReq.append(False)

            self.stack.append(self.currentNode)
            self.currentNode = self.currentNode.addFieldNode(attribute)

            if isinstance(node, BaseSuite):
                self.currentNode.setParentFieldName(self.parentFieldStack[-1])

            # lists and tuples can have elements of same type > ordered node
            if isinstance(getattr(node, attribute), (list, tuple)):
                self.currentNode.makeOrdered()

    def on_leave_attribute(self, original_node: CSTNode, attribute: str) -> None:
        if getattr(original_node, attribute) is None:  # ignore empty attributes
            return

        if isinstance(getattr(original_node, attribute), (list, tuple)) and len(getattr(original_node, attribute)) == 0:
            return

        if isinstance(getattr(original_node, attribute), BaseSuite):
            # attribute can not be removed - did not create field-node
            self.parentFieldStack.pop()
            return

        if not self.visitReq.pop():
            self.currentNode = self.stack.pop()


def normalizeEmptyLines(content: str) -> str:
    lines = []
    ok = " \t\n"
    for line in content.split('\n'):
        if all(c in ok for c in line):
            lines.append('')
        else:
            lines.append(line)
    return '\n'.join(lines)


def parseJupyterCellLines(cell, parentNode):
    parentNode.makeOrdered()
    # metadata??
    for source_line in cell["source"]:
        parentNode.addJupyterLineNode(source_line)


def parseJson(cell, parentNode):
    if isinstance(cell, dict):
        objectNode = parentNode.addJsonObjectNode()
        for key, value in cell.items():
            fieldNode = objectNode.addJsonFieldNode(key)
            parseJson(value, fieldNode)

    elif isinstance(cell, list):
        arrayNode = parentNode.addJsonArrayNode()
        for element in cell:
            parseJson(element, arrayNode)

    elif isinstance(cell, bool):
        parentNode.addJsonBooleanNode(cell)
    elif isinstance(cell, str):
        parentNode.addJsonStringNode(cell)
    elif isinstance(cell, int):
        parentNode.addJsonIntegerNode(cell)
    elif isinstance(cell, float):
        parentNode.addJsonRealNumberNode(cell)
    elif cell is None:
        parentNode.addJsonNullValueNode()
    else:
        raise Exception("Unexpected value found in Json-Cell")


def parseJupyterCellArray(cellArray, parentNode):
    if isinstance(cellArray, list):
        arrayNode = parentNode.addJsonArrayNode()
        for cell in cellArray:
            cellNode = arrayNode.addJupyterCellNode(str(cell["cell_type"]))
            if cell["cell_type"] == "code":
                try:
                    st = ''.join(cell["source"])
                    parsedCode = parse_module(normalizeEmptyLines(st))
                    cellNode.setParseType("code")
                    parsedCode.visit(CSTReader(cellNode))
                except ParserSyntaxError:
                    cellNode.setParseType("markdown")
                    parseJupyterCellLines(cell, cellNode)
                except Exception:
                    logger.severe(traceback.format_exc())

            elif cell["cell_type"] == "markdown":
                cellNode.setParseType("markdown")
                parseJupyterCellLines(cell, cellNode)
                # metadata : dict {collapsed: False}
    else:
        raise Exception("Expected Jupyter Cell-Array (list), but got " + str(type(cellArray)))


def read(filename: str):
    start = timer()
    logger.info(f"PY: Starting Reader Script for {filename}")

    # access java gateway and entry point
    gateway = JavaGateway()
    ep = gateway.entry_point

    f = open(filename, "r", -1, "UTF-8")  # open file

    if filename.endswith(".py"):
        data = normalizeEmptyLines(f.read())
        logger.info(f"PY: Successfully read file (%4.3fms)" % ((timer() - start) * 1000))
        # parse code

        start = timer()
        code = parse_module(data)
        logger.info(f"PY: Successfully parsed to LibCST-Graph (%4.3fms)" % ((timer() - start) * 1000))

        start = timer()
        code.visit(CSTReader(ep.getStartingNode()))
        logger.info(f"PY: Successfully traversed and parsed to ECCO-Artifact-Graph (%4.3fms)" % ((timer() - start) * 1000))

    elif filename.endswith(".ipynb"):
        data = json.load(f)

        if isinstance(data, dict):
            objectNode = ep.getStartingNode().addJsonObjectNode()
            for key, value in data.items():
                fieldNode = objectNode.addJsonFieldNode(key)
                if key == "cells":
                    parseJupyterCellArray(value, fieldNode)
                else:
                    parseJson(value, fieldNode)
        else:
            raise Exception("Unexpected data found in Jupyter Notebook")

    elif filename.endswith(".json"):
        data = json.load(f)
        root = ep.getStartingNode()
        parseJson(data, root)
    else:
        raise Exception(f"Trying to read file with unknown file extension ({filename})")

    f.close()


if __name__ == '__main__':
    main_start = timer()

    logger = JavaGateway().entry_point.getLogger()
    try:
        read(sys.argv[1])
    except Exception as e:
        logger.severe(traceback.format_exc())
        exit(1)

    logger.info("PY: Successfully finished Reader-Script (%4.3fms total)" % ((timer() - main_start) * 1000))
