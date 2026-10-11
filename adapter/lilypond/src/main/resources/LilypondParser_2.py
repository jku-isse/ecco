import os
import re
import sys
from py4j.java_gateway import JavaGateway, GatewayParameters

# The LilyPond reader: the parse is lymodel's (the lilypond-idea-plugin's python/ directory, from
# LYPYTHON when that names one, else installed), whose tree-sitter grammar is the plugin's own. What
# goes to ECCO is what the reader sent when it read with parce (LilypondParser_1.py, until lybar-1),
# as far as ECCO's Java side tells it apart: tokens with parce's action names (the writer spaces by them, the
# viewer colors by them, and a token is identified by its text and action), and the contexts the
# reader's LilyEccoTransformer and artifact factories look for - LilyPond.list around a variable's
# name, LilyPond.string around a string, LilyPond.lyriclist around lyrics, the comment contexts.
# Other contexts follow the syntax (a block, a { } or << >> list, a chord, Scheme), not parce's
# lexer states.

# the ECCO process's gateway - on the port it passes in ECCO_PY4J_PORT (py4j's default otherwise)
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=int(os.environ.get("ECCO_PY4J_PORT", "25333"))))
ep = gateway.entry_point

f = open(sys.argv[1], "r", -1, "UTF-8")
s = f.read()
f.close()

if os.environ.get("LYPYTHON"):
    sys.path.insert(0, os.environ["LYPYTHON"])
try:
    from tree_sitter import Parser
    from lymodel.lybar.grammar import LANGUAGE
    from lymodel.lilydata import lilypond, words
except ImportError as e:
    print("Reading LilyPond needs lymodel: %s.\n"
          "Install it into the python that runs this script (python -m pip install <lilypond-idea-plugin>/python)\n"
          "or set LYPYTHON to that python/ directory." % e, file=sys.stderr)
    sys.exit(3)

MUSICAL = bool(os.environ.get("ECCO_LILYPOND_MUSICAL_TOKENS"))
VARIABLE_CONTEXTS = bool(os.environ.get("ECCO_LILYPOND_VARIABLE_CONTEXTS"))

# Musical tokens (a repository that records lilypond.musicalTokens=true): each note, rest and chord
# end is ONE token - pitch, octave marks, accidental marks, duration, dots, scaling - carrying what it
# means however it is spelled (absolute pitch, written-out duration), from lymodel's lybar. A file
# whose notes lymodel cannot pair (\fixed, say) keeps its notes' text alone - the same in every commit.
meanings = {}
if MUSICAL:
    try:
        from lymodel.lybar.normalize import note_values
        meanings = note_values(s)
    except Exception as e:
        print("no musical tokens: %s" % e, file=sys.stderr)

data = s.encode("utf-8")
tree = Parser(LANGUAGE).parse(data)

# tree-sitter counts UTF-8 bytes, ECCO characters
_chars = {}
_n = 0
for _i, _ch in enumerate(s):
    _chars[_n] = _i
    _n += len(_ch.encode("utf-8"))
_chars[_n] = len(s)


def start(node):
    return _chars[node.start_byte]


def end(node):
    return _chars[node.end_byte]


def text(node):
    return s[start(node):end(node)]


# --------------------------------------------------------------------------------------------------
# What goes to ECCO. Whitespace goes before the contexts opened at the next token, as parce sent it.

last = 0      # end of what was sent


def gap(pos):
    """Sends what lies between the last token and pos: whitespace, or - never lost - a token."""
    global last
    if pos > last:
        between = s[last:pos]
        if between.strip():
            ep.addToken(last, between, "Text")
        else:
            ep.addWhitespace(last, between)
        last = pos


def push(pos, *names):
    gap(pos)
    for name in names:
        ep.pushContext(name)


def pop(levels):
    if levels:
        ep.popContext(-levels)


def token(pos, txt, action, meaning=None):
    global last
    gap(pos)
    if meaning is None:
        ep.addToken(pos, txt, action)
    else:
        ep.addToken(pos, txt, action, meaning)
    last = pos + len(txt)


# --------------------------------------------------------------------------------------------------
# Action names: parce's, by the leaf's syntax and text.

KEYWORDS = (set(words.PARSER_KEYWORDS) | set(words.KEYWORDS) | set(words.LEXER_DIRECTIVES)) \
    - {"tempo", "repeat", "alternative", "default"}
LYRIC_KEYWORDS = {"lyricmode", "lyricsto", "addlyrics", "lyrics"}
MARKUP_KEYWORDS = {"markup", "markuplist"}
DYNAMICS = set(words.DYNAMIC_WORDS)
ARTICULATIONS = set(words.ARTICULATIONS) | set(words.SCRIPTS)
MARKUP_COMMANDS = set(words.MARKUP_COMMANDS) | set(words.MARKUP_LIST_COMMANDS)
EVENT_COMMANDS = {k.lstrip("\\") for k, v in words.MUSIC.items()
                  if str(v).endswith("SpanEvent") or str(v) in ("GlissandoEvent", "RepeatTieEvent", "LaissezVibrerEvent",
                                                               "ArpeggioEvent")} | {"melisma", "melismaEnd"}
BUILTINS = set(words.MUSIC_COMMANDS) | set(words.MUSIC_FUNCTIONS) | {k.lstrip("\\") for k in words.MUSIC}
NAMED_DURATIONS = set(words.NAMED_DURATIONS)
MODES = {"major", "minor", "ionian", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian"}
BLOCKS = {"header", "paper", "layout", "midi", "book", "bookpart", "score"}
RESTS = {"r", "R", "s"}
UNITS = set(lilypond.PAPER_UNITS)
SCHEME_KEYWORDS = {"define", "define-public", "define-markup-command", "define-music-function", "define-scheme-function",
                   "define-void-function", "define-event-function", "lambda", "let", "let*", "letrec", "if", "cond", "case",
                   "else", "and", "or", "when", "unless", "begin", "set!", "quote", "quasiquote", "unquote", "do"}
NOTE_NAMES = {name for names in lilypond.NOTE_NAMES.values() for name in names} | RESTS | {"q"}

SPANNERS = {"[": "Name.Symbol.Spanner.Beam", "]": "Name.Symbol.Spanner.Beam",
            "(": "Name.Symbol.Spanner.Slur", ")": "Name.Symbol.Spanner.Slur",
            "\\(": "Name.Symbol.Spanner.Slur.Phrasing", "\\)": "Name.Symbol.Spanner.Slur.Phrasing",
            "~": "Name.Symbol.Spanner.Tie", "\\[": "Name.Symbol.Spanner.Ligature",
            "\\]": "Name.Symbol.Spanner.Ligature"}

LEAVES = {
    ("duration", "event"): "Literal.Number.Duration",
    ("lyric_syllable", "lyric_event"): "Text.Lyric.LyricText",
    ("lyric_hyphen", None): "Delimiter.Lyric.LyricHyphen",
    ("lyric_extender", None): "Delimiter.Lyric.LyricExtender",
    ("lyric_skip", None): "Delimiter.Lyric.LyricSkip",
    ("|", None): "Delimiter.Separator.PipeSymbol",
    ("embedded_scheme_prefix", None): "Delimiter.ModeChange.SchemeStart",
    ("fraction", None): "Literal.Number.Fraction",
    ("<", "chord"): "Delimiter.Chord.Start",
    (">", "chord"): "Delimiter.Chord.End",
    ("<<", None): "Delimiter.Bracket.Start",
    (">>", None): "Delimiter.Bracket.End",
    ("\\!", None): "Name.Builtin.Dynamic",
    ("\\<", None): "Name.Builtin.Dynamic",
    ("\\>", None): "Name.Builtin.Dynamic",
    ("(", "scheme_list"): "Delimiter.OpenParen",
    (")", "scheme_list"): "Delimiter.CloseParen",
    ("'", None): "Delimiter.Scheme.Quote",
    ("`", None): "Delimiter.Scheme.Quote",
    (",", "scheme_unquote"): "Delimiter.Scheme.Quote",
    ("scheme_boolean", None): "Literal.Number.Boolean",
    ("scheme_symbol", None): "Name",
    ("#{", None): "Delimiter.Bracket.LilyPond.Start",
    ("#}", None): "Delimiter.Bracket.LilyPond.End",
    ("decimal_number", None): "Literal.Number.Float",
    ("escape_sequence", None): "Literal.String.Escape",
    ("parallel_music_separator", None): "Delimiter.Separator.VoiceSeparator",
    ("*", None): "Literal.Number.Duration",
    (".", None): "Literal.Number.Duration.Dot",
    ("/", None): "Delimiter.Separator.Chord",
}


def after_direction(node):
    """Whether node follows a direction sign (- ^ _) directly, as the > in ^>."""
    prev = node.prev_sibling
    return prev is not None and text(prev) in ("-", "^", "_") and end(prev) == start(node)


def in_call(node, names):
    p = node.parent
    while p is not None:
        if p.type == "music_call" and call_name(p) in names:
            return True
        p = p.parent
    return False


def in_markup(node):
    return in_call(node, MARKUP_KEYWORDS)


def in_block(node):
    """Inside \\header { } and the like: an assignment there names a setting (not in a \\with { })."""
    p = node.parent
    while p is not None:
        if p.type == "music_call" and call_name(p) in BLOCKS or block_keyword(p) is not None:
            return True
        if p.type == "context_modification":
            return False
        if p.type == "lilypond_program":
            return False
        p = p.parent
    return False


def in_context_block(node):
    """In \\context { } of a \\layout or \\midi: \\Staff and the like name the context changed."""
    p = node.parent
    while p is not None and p.type != "lilypond_program":
        if context_keyword(p) or p.type == "context_modification":
            return True
        p = p.parent
    return False


def block_keyword(node):
    """The \\header, \\midi ... a { } belongs to when tree-sitter parsed them as two siblings."""
    if node.type != "expression_block":
        return None
    prev = node.prev_sibling
    if prev is not None and prev.type == "escaped_word" and text(prev)[1:] in BLOCKS:
        return text(prev)[1:]
    return None


def context_keyword(node):
    prev = node.prev_sibling
    return node.type == "expression_block" and prev is not None and text(prev) == "\\context"


def call_name(node):
    function = node.child_by_field_name("function") or (node.children[0] if node.children else None)
    return text(function)[1:] if function is not None and text(function).startswith("\\") else ""


def escaped_word(node):
    word = text(node)[1:]
    parent = node.parent.type if node.parent is not None else ""
    if word in LYRIC_KEYWORDS:
        return "Keyword.Lyric"
    if word in MARKUP_KEYWORDS:
        return "Keyword.Markup"
    if word in MARKUP_COMMANDS and in_markup(node):
        return "Name.Function.Markup"
    if word in DYNAMICS:
        return "Name.Builtin.Dynamic"
    if word in ARTICULATIONS or word in EVENT_COMMANDS:
        return "Name.Script.Articulation"
    if word in UNITS:
        return "Name.Builtin.Unit"
    if word[:1].isupper() and in_context_block(node):
        return "Name.Builtin.Context"
    if word in KEYWORDS:
        return "Keyword"
    if word in MODES:
        return "Name.Type"
    if word in NAMED_DURATIONS:
        return "Literal.Number.Duration"
    if word in BUILTINS:
        return "Name.Builtin"
    return "Name.Variable"


def symbol(node):
    parent = node.parent.type if node.parent is not None else ""
    word = text(node)
    if parent == "pitch":
        if in_markup(node):
            return "Text"
        if in_call(node, ("drummode", "drums")):
            return "Text.Music.Pitch.Drum"
        before = previous_word(node)
        if before == "\\repeat":
            return "Name.Type"
        if before == "\\clef":
            return "Name.Symbol"
        if word not in NOTE_NAMES:
            return "Name.Variable"
        return "Text.Music.Rest" if word in RESTS else "Text.Music.Pitch"
    if parent == "music_call":
        return "Text.Music.Pitch"     # \relative c'' - the reference pitch
    if parent == "assignment_lhs":
        assignment = node.parent.parent
        if assignment.parent is not None and assignment.parent.type == "lilypond_program":
            return "Name.Variable.Definition"
        return "Name.Attribute" if in_block(node) else "Name.Variable"
    if parent == "named_context":
        field = named_field(node)
        return "Name.Constant.Context" if field == "type" else "Name.Variable"
    if parent == "property_expression":
        return property_part(node)
    if parent == "property_call":
        return "Name.Symbol"
    return "Name.Variable"


def named_field(node):
    p = node.parent
    for i, c in enumerate(p.children):
        if c.id == node.id:
            return p.field_name_for_child(i)
    return None


def property_part(node):
    """Staff.TimeSignature.color: a context, a grob, a property - by the command and the place."""
    top = node.parent
    while top.parent is not None and top.parent.type == "property_expression":
        top = top.parent
    path = []
    stack = [top]
    while stack:
        n = stack.pop()
        if n.type == "symbol":
            path.append(n)
        stack.extend(reversed(n.children))
    index = next(i for i, c in enumerate(path) if c.id == node.id)
    if len(path) == 1 or not text(node)[:1].isupper():
        return "Name.Variable"
    if any(text(c)[:1].isupper() for c in path[index + 1:]):
        return "Name.Constant.Context"
    call = node.parent
    while call is not None and call.type != "property_call":
        call = call.parent
    command = call_name(call) if call is not None else ""
    return "Name.Constant.Context" if command in ("set", "unset") else "Name.Object.Grob"


def previous_word(node):
    """The escaped word just before node's event, as \\clef in \\clef treble."""
    p = node
    while p is not None and p.prev_sibling is None:
        p = p.parent
    prev = p.prev_sibling if p is not None else None
    while prev is not None and prev.child_count:
        prev = prev.children[-1]
    return text(prev) if prev is not None and prev.type == "escaped_word" else None


def punctuation(node):
    t = text(node)
    parent = node.parent.type if node.parent is not None else ""
    if parent in ("post_event", "punctuation"):
        if t in SPANNERS:
            return SPANNERS[t]
        p = node.parent
        if p.type == "post_event" and p.child_by_field_name("direction") is not None \
                and p.child_by_field_name("direction").id == node.id:
            return "Delimiter.Direction"
        if t in ("-", "^", "_") and node.next_sibling is not None:
            return "Delimiter.Direction"
        return "Literal.Character.Script"
    if parent in ("octave_marks", "music_call"):
        return "Text.Music.Pitch.Octave"
    if parent == "octave_check":
        return "Text.Music.Pitch.Octave.OctaveCheck"
    if parent == "pitch":
        return "Text.Music.Pitch.Accidental"
    if t == "=":
        return "Delimiter.Operator.Assignment"
    if parent == "property_expression" or t in (".", ","):
        return "Delimiter.Separator"
    return "Delimiter"


def number(node):
    parent = node.parent.type if node.parent is not None else ""
    if parent in ("post_event",):
        return "Literal.Number.Fingering"
    prev = node.prev_sibling
    if prev is not None and text(prev) == "*":
        return "Literal.Number.Duration.Scaling"
    if prev is not None and text(prev) == "=":
        return "Literal.Number"
    if prev is not None and text(prev) in ("volta", "unfold", "percent", "tremolo", "segno"):
        return "Literal.Number"
    if prev is not None and text(prev) in ("-", "^", "_") and end(prev) == start(node):
        return "Literal.Number.Fingering"
    if parent in ("assignment", "property_call", "chord"):
        return "Literal.Number"
    return "Literal.Number.Duration"


def action(node):
    t = node.type
    parent = node.parent.type if node.parent is not None else ""
    if t == "escaped_word":
        return escaped_word(node)
    if t == "symbol":
        return symbol(node)
    if t == "punctuation":
        return punctuation(node)
    if t == "unsigned_integer":
        return number(node)
    if t in ("{", "}"):
        if in_markup(node):
            return "Delimiter.Bracket.Markup.Start" if t == "{" else "Delimiter.Bracket.Markup.End"
        return "Delimiter.Bracket.Start" if t == "{" else "Delimiter.Bracket.End"
    if t in SPANNERS and parent in ("post_event", "punctuation", "ligature"):
        return SPANNERS[t]
    if parent == "punctuation" and after_direction(node.parent):
        # ^> after a word tree-sitter doesn't know as a post-event (\\sfpp^>): still the accent's mark
        return "Literal.Character.Script"
    if t in ("-", "^", "_") and parent == "punctuation":
        return "Delimiter.Direction"
    if t == "=":
        return "Delimiter.Operator.Assignment"
    if t == "scheme_symbol":
        word = text(node)
        return "Delimiter.Dot" if word == "." else "Keyword" if word in SCHEME_KEYWORDS else "Name"
    if t == ":" or t == "punctuation" and text(node) == ":":
        return "Delimiter.Separator.Chord"
    return LEAVES.get((t, parent)) or LEAVES.get((t, None)) or "Text"


# --------------------------------------------------------------------------------------------------
# Contexts.

def is_lyrics(block):
    p = block.parent
    if p is not None and (p.type == "lyrics_call" or p.type == "music_call" and call_name(p) in LYRIC_KEYWORDS):
        return True
    return any(c.type == "lyric_event" for c in block.children)


def contexts(node):
    t = node.type
    parent = node.parent
    if t == "escaped_word" and action(node) == "Name.Variable":
        return ["LilyPond.identifier_ref"]
    if t == "escaped_word" and text(node)[1:] in MARKUP_KEYWORDS:
        return ["LilyPond.markup"]
    if t == "symbol" and parent is not None and parent.type == "named_context":
        return ["LilyPond.list"]
    if t == "punctuation" and parent is not None and parent.type == "post_event" \
            and action(node) == "Literal.Character.Script":
        return ["LilyPond.script"]
    if node.child_count == 0 and parent is not None and parent.type == "punctuation" \
            and action(node) == "Literal.Character.Script":
        return ["LilyPond.script"]
    if t in ("unsigned_integer", "decimal_number") and node.prev_sibling is not None \
            and text(node.prev_sibling) == "=":
        return ["LilyPond.list"]
    if parent is not None and parent.type == "lyrics_call" and named_field(node) == "voice":
        return ["LilyPond.lyricsto"] + contexts_of_value(node)
    return contexts_of_value(node)


def contexts_of_value(node):
    t = node.type
    parent = node.parent
    if t == "assignment":
        if VARIABLE_CONTEXTS and parent is not None and parent.type == "lilypond_program":
            lhs = next((c for c in node.children if c.type == "assignment_lhs"), None)
            if lhs is not None:
                return ["LilyPond.assignment." + text(lhs)]
        return []
    if t == "context_modification":
        return ["LilyPond.layout_context"]
    if t in ("assignment_lhs", "property_expression"):
        return ["LilyPond.list"]
    if t == "music_call":
        name = call_name(node)
        if name in BLOCKS:
            return ["LilyPond." + name]
        return []
    if t in ("expression_block", "parallel_music"):
        if parent is not None and parent.type == "music_call" and call_name(parent) in BLOCKS \
                or block_keyword(node) is not None:
            return []
        if in_markup(node):
            return ["LilyPond.markuplist"]
        if is_lyrics(node):
            return ["LilyPond.lyriclist"]
        if parent is not None and parent.type == "context_modification" or context_keyword(node):
            return []
        if in_call(node, ("drummode", "drums")):
            return ["LilyPond.drumlist"]
        return ["LilyPond.musiclist"]
    if t == "string":
        return ["LilyPond.string"] if in_markup(node) else ["LilyPond.list", "LilyPond.string"]
    if t == "embedded_scheme":
        return ["LilyPond.list", "SchemeLily.scheme"]
    if t == "embedded_scheme_text":
        return ["SchemeLily.argument"]
    if t == "scheme_list":
        return ["SchemeLily.list"]
    if t == "scheme_string":
        return ["SchemeLily.string"]
    if t == "scheme_number":
        return ["SchemeLily.number"]
    if t == "chord":
        return ["LilyPond.chord"]
    if t == "comment" or t == "scheme_comment":
        return ["LilyPond.multiline_comment" if text(node).startswith("%{") else "LilyPond.singleline_comment"]
    return []


# --------------------------------------------------------------------------------------------------
# Tokens that are more than one leaf.

NOTE_PARTS = {"pitch", "duration"}


def note(event):
    """A note or rest as ONE token (musical tokens): its pitch and duration as written, with its
    meaning; post-events stay tokens of their own."""
    parts = [c for c in event.children if c.type in NOTE_PARTS]
    if not parts or parts[0].type != "pitch":
        return False
    first = parts[0]
    joined_end = end(first)
    for c in parts[1:]:
        if start(c) != joined_end:
            break
        joined_end = end(c)
    pos = start(first)
    name = first.child_by_field_name("name")
    act = symbol(name) if name is not None else "Text.Music.Pitch"
    if act not in ("Text.Music.Pitch", "Text.Music.Rest"):
        return False
    token(pos, s[pos:joined_end], act, meanings.get(pos))
    for c in event.children:
        if start(c) >= joined_end:
            walk(c)
    return True


def chord_end(node, duration):
    """`>` and the chord's duration as one token (musical tokens), with the chord's meaning."""
    pos = start(node)
    stop = end(duration) if duration is not None and start(duration) == end(node) else end(node)
    token(pos, s[pos:stop], "Delimiter.Chord.End", meanings.get(pos))
    return stop


def plain_duration(node):
    """4. or 1*3/4 as parce split it: the length, its dots, its scaling."""
    pos = start(node)
    for m in re.finditer(r"\\?[A-Za-z]+|\d+|\.+|\*|/|\s+", text(node)):
        p = pos + m.start()
        piece = m.group(0)
        if piece.isspace():
            continue
        if piece.startswith("."):
            token(p, piece, "Literal.Number.Duration.Dot")
        elif piece == "*" or piece.isdigit() and m.start() == 0 or piece.startswith("\\"):
            token(p, piece, "Literal.Number.Duration")
        else:
            token(p, piece, "Literal.Number.Duration.Scaling")


def leaf(node):
    t = node.type
    if t in ("comment", "scheme_comment"):
        token(start(node), text(node), "Comment")
    elif t == "duration" and not MUSICAL:
        plain_duration(node)
    else:
        token(start(node), text(node), action(node))


def walk(node):
    names = contexts(node)
    if names:
        push(start(node), *names)
    t = node.type
    if t in ("string", "scheme_string", "scheme_number"):
        # one token, a number however it is signed: as parce's sign the writer kept the next word
        # from it (#-3b)
        token(start(node), text(node), "Literal.String" if t != "scheme_number" else "Literal.Number.Decimal")
    elif t in ("octave_marks", "octave_check") and node.child_count:
        token(start(node), text(node), action(node.children[0]))
    elif t == "event" and MUSICAL and node.child_by_field_name("pitch") is not None and not in_markup(node) \
            and note(node):
        pass
    elif t == "chord" and MUSICAL:
        children = node.children
        skip_until = -1
        for i, c in enumerate(children):
            if start(c) < skip_until:
                continue
            if c.type == ">":
                following = node.next_sibling
                skip_until = chord_end(c, following if following is not None and following.type == "duration" else None)
            elif c.type == "event" and c.child_by_field_name("pitch") is not None:
                note(c)
            else:
                walk(c)
    elif t == "event" and MUSICAL and any(c.type == "chord" for c in node.children):
        # the chord's duration is joined to its `>` (chord_end) - skip it here
        chord = next(c for c in node.children if c.type == "chord")
        walk(chord)
        for c in node.children:
            if c.type == "duration" and start(c) == end(chord):
                continue
            if c is not chord and c.id != chord.id:
                walk(c)
    elif t == "music_call" and MUSICAL and call_name(node) == "relative":
        for c in node.children:
            if c.type == "symbol":
                # the reference pitch with its octave marks, as one token with its meaning
                pos = start(c)
                stop = end(c)
                nxt = c.next_sibling
                while nxt is not None and nxt.type == "punctuation" and start(nxt) == stop:
                    stop = end(nxt)
                    nxt = nxt.next_sibling
                token(pos, s[pos:stop], "Text.Music.Pitch", meanings.get(pos))
            elif start(c) >= last:
                walk(c)
    elif node.child_count == 0:
        leaf(node)
    else:
        children = node.children
        i = 0
        while i < len(children):
            c = children[i]
            if c.type == "escaped_word" and i + 1 < len(children) and context_keyword(children[i + 1]):
                # \\context { } in a \\layout: the keyword and its block in one layout context
                push(start(c), "LilyPond.layout_context")
                walk(c)
                walk(children[i + 1])
                pop(1)
                i += 2
                continue
            if t != "music_call" and c.type == "escaped_word" and i + 1 < len(children) \
                    and block_keyword(children[i + 1]) is not None:
                # \\midi { } as two siblings: a block like any other
                push(start(c), "LilyPond." + text(c)[1:])
                walk(c)
                walk(children[i + 1])
                pop(1)
                i += 2
                continue
            walk(c)
            i += 1
    if names:
        pop(len(names))


walk(tree.root_node)
gap(len(s))
