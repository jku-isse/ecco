import os
import sys
from py4j.java_gateway import JavaGateway, GatewayParameters
import parce
from parce.lang.lilypond import LilyPond

# the ECCO process's gateway - on the port it passes in ECCO_PY4J_PORT (py4j's default otherwise)
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=int(os.environ.get("ECCO_PY4J_PORT", "25333"))))
ep = gateway.entry_point

f = open(sys.argv[1], "r", -1, "UTF-8")
s = f.read()
f.close()

lastPos = 0
for e in parce.events(LilyPond.root, s):
    pop = 0
    if e.target:
        ep.popContext(e.target.pop)
        first = e.lexemes[0]
        if first[0] > lastPos:
            ep.addWhitespace(lastPos, s[lastPos:first[0]])
            lastPos = first[0] + len(first[1])
        for c in e.target.push:
            ep.pushContext(c.fullname)

    for tpl in e.lexemes:
        if tpl[0] > lastPos:
            ep.addWhitespace(lastPos, s[lastPos:tpl[0]])
        ep.addToken(tpl[0], tpl[1], str(tpl[2]))
        lastPos = tpl[0] + len(tpl[1])

ep.addWhitespace(lastPos, s[lastPos:])
