#!/usr/bin/env python3
"""Generates doc/ecco-architecture.drawio (pages: Architecture, Gradle modules).

Usage: python3 doc/gen_architecture_drawio.py

Edit the boxes/edges below and re-run rather than hand-editing the .drawio,
otherwise manual changes are overwritten on the next run. Keep module lists in
sync with settings.gradle and */build.gradle.
"""
import os
from xml.sax.saxutils import escape
import itertools
ids = itertools.count(2)

C = {  # fill, stroke
 'front': ('#dae8fc', '#6c8ebf'),
 'svc':   ('#d5e8d4', '#82b366'),
 'store': ('#fff2cc', '#d6b656'),
 'base':  ('#ffe6cc', '#d79b00'),
 'found': ('#e1d5e7', '#9673a6'),
 'adapt': ('#f8cecc', '#b85450'),
 'ext':   ('#f5f5f5', '#666666'),
}

class Page:
    def __init__(s, name, pid):
        s.name, s.pid, s.cells = name, pid, []
    def box(s, x, y, w, h, label, kind, parent='1', container=False, bold=False, dashed=False, align='center', fs=11):
        i = f'{s.pid}-{next(ids)}'
        f, st = C[kind]
        style = (f'rounded=1;whiteSpace=wrap;html=1;fillColor={f};strokeColor={st};fontSize={fs};arcSize=6;')
        if container:
            style += 'verticalAlign=top;align=left;spacingLeft=8;spacingTop=2;fontStyle=1;container=1;collapsible=0;'
        else:
            style += f'align={align};' + ('fontStyle=1;' if bold else '')
        if dashed: style += 'dashed=1;'
        s.cells.append(f'<mxCell id="{i}" value="{escape(label, {chr(34): "&quot;"})}" style="{style}" vertex="1" parent="{parent}"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')
        return i
    def text(s, x, y, w, h, label, fs=11, parent='1', align='left'):
        i = f'{s.pid}-{next(ids)}'
        s.cells.append(f'<mxCell id="{i}" value="{escape(label, {chr(34): "&quot;"})}" style="text;html=1;whiteSpace=wrap;align={align};verticalAlign=top;fontSize={fs};" vertex="1" parent="{parent}"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')
        return i
    def edge(s, a, b, label='', dashed=False, color='#333333', ex=None, ey=None, nx=None, ny=None):
        i = f'{s.pid}-{next(ids)}'
        style = f'edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=block;endFill=1;strokeColor={color};fontSize=10;labelBackgroundColor=#ffffff;strokeWidth=1.5;'
        if ex is not None: style += f'exitX={ex};exitY={ey};exitDx=0;exitDy=0;'
        if nx is not None: style += f'entryX={nx};entryY={ny};entryDx=0;entryDy=0;'
        if dashed: style += 'dashed=1;'
        s.cells.append(f'<mxCell id="{i}" value="{escape(label)}" style="{style}" edge="1" parent="1" source="{a}" target="{b}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    def xml(s):
        return (f'<diagram id="{s.pid}" name="{escape(s.name)}"><mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1654" pageHeight="1169" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>'
                + '\n'.join(s.cells) + '</root></mxGraphModel></diagram>')

def items(p, parent, x0, y0, w, h, labels, kind, cols, gap=10):
    out = []
    for k, lab in enumerate(labels):
        r, c = divmod(k, cols)
        out.append(p.box(x0 + c*(w+gap), y0 + r*(h+gap), w, h, lab, kind, parent=parent, fs=10))
    return out

# ---------------- Page 1: layered architecture ----------------
p = Page('Architecture', 'arch')
p.text(40, 10, 1100, 30, '<b style="font-size:18px">ECCO – Architecture Overview</b>', fs=18)
p.text(40, 40, 1300, 20, 'Layers top→bottom depend downward. Colors = layer. Arrows = main runtime calls / data flow. Source of truth: settings.gradle + */build.gradle. Last reviewed: 2026-10-08 (6c085b13).', fs=10)

# Frontends
F = p.box(40, 70, 1260, 170, 'Frontends', 'front', container=True)
gui = p.box(10, 30, 560, 130, 'ecco-gui  (JavaFX · AtlantaFX · GraphStream)', 'front', parent=F, container=True)
items(p, gui, 10, 30, 125, 26, ['Commits / Commit detail', 'Associations', 'Artifacts (tree / table)', 'Variants / Checkout',
      'Features / Feature model', 'Knowledge &amp; POG graphs', 'Constraint suggestions', 'Charts / Comparison',
      'Import from Git', 'Remotes: fork/pull/push', 'Settings / Preferences', 'Notifications'], 'front', 4, gap=8)
cli = p.box(580, 30, 330, 130, 'ecco-cli  (argparse4j)', 'front', parent=F, container=True)
p.text(10, 26, 310, 100, 'Command / CommandRegister<br>init · status · commit · checkout · features · traces · adapters · property · remotes · fetch · pull · push · fork · dependencygraph · suggestconstraints · minimizepreview', fs=10, parent=cli)
rest = p.box(920, 30, 330, 130, 'ecco-rest  (Micronaut HTTP API)', 'front', parent=F, container=True)
p.text(10, 26, 310, 100, 'RepositoryController · CommitController · FeatureController · VariantController<br>RepositoryService / FileRepositoryService · RepositoryHandler<br>authorisation · models (DTOs)', fs=10, parent=rest)

# Service
S = p.box(40, 260, 1260, 250, 'ecco-service  (application layer, Guice-wired)', 'svc', container=True)
es = p.box(10, 30, 290, 100, '<b>EccoService</b><br>facade for all operations: init/open/close, commit, checkout, compose, fork/pull/push, features, constraints<br><i>Guice injector · properties</i>', 'svc', parent=S, fs=10)
lr = p.box(10, 140, 290, 45, '<b>ListenerRegistry</b> · EccoListener<br>Read/Write/Export/Server listeners (events → UI)', 'svc', parent=S, fs=10)
vm = p.box(10, 195, 140, 45, 'VariantManager', 'svc', parent=S, fs=10)
cs = p.box(160, 195, 140, 45, 'ConstraintService', 'svc', parent=S, fs=10)
mining = p.box(310, 30, 300, 210, 'mining  (presence conditions &amp; constraints)', 'svc', parent=S, container=True)
items(p, mining, 10, 28, 135, 30, ['ConstraintMiner', 'ConstraintViolationChecker', 'PresenceConditionMinimizer', 'ParallelMinimization',
      'SurplusLatticeAbsorber', 'SurplusModuleSuppressor', 'FeatureSelectionPropagator', 'FeatureModelFormula',
      'Module/ConfigurationBridge', 'AcceptedConstraints'], 'svc', 2, gap=8)
integ = p.box(620, 30, 250, 210, 'integrations', 'svc', parent=S, container=True)
rss = p.box(10, 28, 230, 45, '<b>RemoteSyncService</b><br>fetch / pull / push over TCP sockets', 'svc', parent=integ, fs=10)
git = p.box(10, 83, 230, 45, '<b>git</b>: GitHistoryReader, GitCommitInfo<br>(JGit – Import from Git)', 'svc', parent=integ, fs=10)
llm = p.box(10, 138, 230, 45, '<b>llm</b>: LlmFeatureSuggestionClient<br>(HTTP; feature suggestions on import)', 'svc', parent=integ, fs=10)
aspi = p.box(880, 30, 370, 100, 'adapter SPI', 'svc', parent=S, container=True)
p.text(10, 20, 350, 78, '<b>ArtifactPlugin</b> (Guice module per adapter)<br>ArtifactReader · ArtifactWriter · ArtifactViewer · ArtifactExporter<br><b>dispatch</b>: DispatchReader / DispatchWriter route files → plugin by pattern<br><b>PythonFinder</b>: the python a py4j adapter runs - Preferences, else python, python3, usual locations; probed for its modules', fs=9, parent=aspi)
sspi = p.box(880, 140, 370, 100, 'storage  (StoragePlugin SPI, chosen via property)', 'store', parent=S, container=True)
ser = p.box(10, 28, 220, 62, '<b>ser</b> – SerPlugin (default)<br>Java serialization, per-entity files, dedup. artifact store, lazy mainTree; implements base.dao', 'store', parent=sspi, fs=10)
mem = p.box(240, 28, 120, 62, '<b>mem</b> – MemPlugin<br>in-memory (tests)', 'store', parent=sspi, fs=10)

# Base
B = p.box(40, 530, 1260, 250, 'ecco-base  (core domain model &amp; algorithms, storage-agnostic)', 'base', container=True)
repo = p.box(10, 30, 290, 90, '<b>repository.Repository</b><br>commit (extract / slice associations), checkout / compose, merge, fork, feature &amp; module bookkeeping', 'base', parent=B, fs=10)
dao = p.box(10, 130, 290, 110, '<b>dao</b> (persistence interfaces)<br>EntityFactory · RepositoryDao · CommitDao · AssociationDao · FeatureDao · RemoteDao · TransactionStrategy', 'base', parent=B, fs=10)
dm = p.box(310, 30, 560, 210, 'domain model', 'base', parent=B, container=True)
items(p, dm, 10, 28, 170, 52, ['<b>core</b><br>Association · Commit · Variant · Constraint · Remote · Checkout · Diff · DependencyGraph',
      '<b>feature</b><br>Feature · FeatureRevision · Configuration',
      '<b>module / counter</b><br>Module · ModuleRevision · Condition (presence conditions) · counters',
      '<b>tree</b> + util.Trees<br>Node · RootNode · treeFusion / slice / merge',
      '<b>artifact</b><br>Artifact · ArtifactData · references',
      '<b>pog</b><br>PartialOrderGraph (sibling ordering)',
      '<b>maintree</b><br>building · factory · retroactive',
      '<b>featuretrace</b><br>FeatureTrace · parser · evaluation'], 'base', 3, gap=8)
comp = p.box(880, 30, 370, 90, '<b>composition</b> (used by Repository checkout)<br>CheckoutComposer · LazyCompositionNode/RootNode · OrderSelector / DefaultOrderSelector · OrderSetterVisitor', 'base', parent=B, fs=10)
p.box(880, 130, 370, 50, '<b>tree.ArtifactDiagnostics</b> · core.Warning<br>(order / surplus / missing warnings on checkout)', 'base', parent=B, fs=10)

# Foundation
Fd = p.box(40, 800, 1260, 80, 'Foundation', 'found', container=True)
logic = p.box(10, 28, 400, 42, '<b>ecco-logic</b> – boolean formulas on LogicNG (SAT, minimization)', 'found', parent=Fd, fs=10)
util = p.box(420, 28, 250, 42, '<b>ecco-util</b> – commons-io helpers', 'found', parent=Fd, fs=10)
p.box(680, 28, 570, 42, 'Third-party (see gradle/libs.versions.toml): Guice · Guava · Eclipse Collections · LogicNG · JGit · Jackson · JavaParser', 'ext', parent=Fd, fs=10)

# Adapters (right column)
A = p.box(1330, 70, 290, 710, 'Artifact adapters  (adapter/*)', 'adapt', container=True)
p.text(10, 22, 270, 48, 'Each = ArtifactPlugin + Reader/Writer(+Viewer). Depend on ecco-service; loaded at runtime (runtimeOnly), enabled via AdapterPreferences. python and lilypond run Python scripts over py4j.', fs=9, parent=A)
items(p, A, 10, 84, 130, 36, ['file', 'text', 'markdown', 'image', 'java', 'java-ast', 'python', 'c', 'cpp', 'typescript',
      'golang', 'lilypond', 'challenge', 'runtime'], 'adapt', 2, gap=8)
p.box(10, 440, 270, 50, '<b>extras-ly</b><br>LilyPond tooling (base + service + adapter-lilypond)', 'adapt', parent=A, fs=10)
p.box(10, 500, 270, 90, 'Not wired into settings.gradle (kept, unbuilt):<br>adapter/ designspace · java6 · java8<br>extras/ cpp · emf · generic · jackson · jpa · perst · php · runtime · uml · xml<br>storage/ neo4j', 'ext', parent=A, dashed=True, fs=9)

# External systems
X = p.box(40, 900, 1580, 90, 'External systems', 'ext', container=True)
fs_ = p.box(10, 28, 300, 50, '<b>Working directory</b> (variant files) ← adapters<br><b>.ecco/</b> repository dir ← storage.ser', 'ext', parent=X, fs=10)
gitx = p.box(320, 28, 220, 50, '<b>Git repositories</b><br>← service.git (JGit)', 'ext', parent=X, fs=10)
llmx = p.box(550, 28, 220, 50, '<b>LLM endpoint</b> (URL/model configurable)<br>← service.llm (HTTP)', 'ext', parent=X, fs=10)
peer = p.box(780, 28, 220, 50, '<b>Remote ECCO repository</b><br>← RemoteSyncService (TCP)', 'ext', parent=X, fs=10)
web = p.box(1010, 28, 300, 50, '<b>ecco-web-client</b> (separate repo)<br>→ ecco-rest (HTTP/JSON)', 'ext', parent=X, fs=10)
pyx = p.box(1320, 28, 250, 50, '<b>Python</b> (libcst · parce · lymodel)<br>← python, lilypond adapters (process + py4j)', 'ext', parent=X, fs=10)

# Edges: layer-to-layer only, fixed ports so they stay tidy when boxes move
p.edge(F, S, 'call EccoService API', ex=0.12, ey=1, nx=0.12, ny=0)
p.edge(S, B, 'Repository + base.dao', ex=0.12, ey=1, nx=0.12, ny=0)
p.edge(B, Fd, 'formulas', ex=0.12, ey=1, nx=0.12, ny=0)
p.edge(Fd, X, '', dashed=True, ex=0.5, ey=1, nx=0.5, ny=0, color='#999999')
p.edge(A, aspi, 'implements', dashed=True, ex=0, ey=0.38, nx=1, ny=0.5)
page1 = p

# ---------------- Page 2: Gradle module dependencies ----------------
q = Page('Gradle modules', 'mods')
q.text(40, 10, 1100, 30, '<b style="font-size:18px">ECCO – Gradle module dependencies</b>', fs=18)
q.text(40, 40, 1200, 20, 'Solid = implementation/api. Dashed = runtimeOnly. Test-only dependencies omitted. Project names are prefixed "ecco-" (settings.gradle).', fs=10)
m = {}
FR = q.box(180, 80, 640, 90, 'frontends', 'front', container=True)
m['rest'] = q.box(20, 28, 160, 50, 'rest', 'front', parent=FR, bold=True)
m['cli'] = q.box(240, 28, 160, 50, 'cli', 'front', parent=FR, bold=True)
m['gui'] = q.box(460, 28, 160, 50, 'gui', 'front', parent=FR, bold=True)
m['extras-ly'] = q.box(1000, 95, 160, 50, 'extras-ly', 'adapt', bold=True)
m['service'] = q.box(420, 250, 160, 50, 'service', 'svc', bold=True)
m['adapters'] = q.box(860, 340, 300, 90, '<b>adapter-*</b> (14)<br>file · text · markdown · image · java · java-ast · python · c · cpp · typescript · golang · lilypond · challenge · runtime', 'adapt', fs=10)
m['util'] = q.box(200, 450, 160, 50, 'util', 'found', bold=True)
m['base'] = q.box(420, 450, 160, 50, 'base', 'base', bold=True)
m['logic'] = q.box(640, 580, 160, 50, 'logic', 'found', bold=True)
m['experiment'] = q.box(1000, 580, 160, 50, 'experiment<br><i>(no main sources)</i>', 'ext')
q.box(180, 670, 640, 70, '<b>runtimeOnly adapter sets</b><br>gui: all 14 · cli: file, text, markdown, image, golang · rest: file, text, image, java', 'ext', fs=10, dashed=True)
q.edge(m['rest'], m['service'], ex=0.5, ey=1, nx=0.2, ny=0)
q.edge(m['cli'], m['service'], ex=0.5, ey=1, nx=0.5, ny=0)
q.edge(m['gui'], m['service'], ex=0.5, ey=1, nx=0.8, ny=0)
q.edge(FR, m['adapters'], 'runtimeOnly (see note)', dashed=True, ex=1, ey=0.5, nx=0.2, ny=0)
q.edge(m['adapters'], m['service'], ex=0.05, ey=0, nx=1, ny=0.5)
q.edge(m['extras-ly'], m['service'], ex=0.2, ey=1, nx=1, ny=0.1)
q.edge(m['extras-ly'], m['adapters'], 'lilypond', ex=0.7, ey=1, nx=0.85, ny=0)
q.edge(m['service'], m['base'], 'api', ex=0.5, ey=1, nx=0.5, ny=0)
q.edge(m['service'], m['util'], ex=0, ey=0.5, nx=0.5, ny=0)
q.edge(m['service'], m['logic'], ex=1, ey=0.9, nx=0.5, ny=0)
q.edge(m['base'], m['logic'], ex=0.5, ey=1, nx=0, ny=0.5)

out = ('<mxfile host="drawio" version="24.0.0">\n' + page1.xml() + '\n' + q.xml() + '\n</mxfile>\n')
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ecco-architecture.drawio')
with open(path, 'w') as f:
    f.write(out)
print('wrote', path)
