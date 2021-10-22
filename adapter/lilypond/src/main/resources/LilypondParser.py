from py4j.java_gateway import JavaGateway
import sys
import parce
from parce.lang.lilypond import LilyPond

gateway = JavaGateway()
ep = gateway.entry_point

f = open(sys.argv[1], "r", -1, "UTF-8")
s = f.read()
f.close()

lastPos = 0
for e in parce.events(LilyPond.root, s):
    ep.openEvent()
    pop = 0
    if e.target:
        pop = e.target.pop
        for c in e.target.push:
            ep.addContext(c.fullname)

#    for tpl in e.tokens:        # until parce v0.13
    for tpl in e.lexemes:      # since parce v0.14
        ws = s[lastPos:tpl[0]]
        ep.addToken(tpl[0], tpl[1], str(tpl[2]), ws)
        lastPos = tpl[0] + len(tpl[1])

    ep.closeEvent(pop)