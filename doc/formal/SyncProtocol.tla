---------------------------- MODULE SyncProtocol ----------------------------
(***************************************************************************)
(* Pull and push between clients and a sync server (RemoteSyncService),    *)
(* by commit replay (FR-D3b/c): what each repository holds, as a sequence  *)
(* of commit ids, and what the requests carry. Commit contents are left    *)
(* out - SyncPropertyTest checks those; this checks every interleaving of  *)
(* requests, lost answers, retries and crashes.                            *)
(*                                                                         *)
(* The server handles one connection after the other, each request's      *)
(* repository work in one transaction under its service's lock: a request  *)
(* is one step. A pull (PULL_COMMITS) is one step too - the client holds   *)
(* its own lock throughout - appending the server's commits it lacks, in   *)
(* the server's order. A push is two requests: COMMIT_IDS, which tells the *)
(* client what the server has, then PUSH_COMMITS with the client's commits *)
(* the server did not have, in the client's order. Between them other     *)
(* clients push and the server commits; the payload can arrive late -      *)
(* after the client gave up waiting (a timeout, a lost answer) and pushed  *)
(* again - or never (a crash). The server takes in a payload's commits it  *)
(* lacks, in the payload's order (CommitReplay#replayInto, which skips the *)
(* ones it has when SkipHad); a merge (FR-D3e) and exclusions (FR-D4a) are *)
(* not modelled.                                                           *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS
    Clients,   \* the repositories syncing with the server
    Ids,       \* commit ids (UUIDs), one per commit, never reused
    SkipHad    \* whether replay skips a commit the receiving repository has (the code: TRUE)

Server == "server"
Repos == Clients \cup {Server}

VARIABLES
    repo,      \* repository -> its commits, in order
    born,      \* repository -> the commits made there, in the order they were made
    used,      \* the ids taken
    pc,        \* client -> "idle", "pushing" (has the server's ids), "waiting" (payload sent)
    known,     \* client -> the server's ids its last COMMIT_IDS returned
    inflight,  \* PUSH_COMMITS payloads not handled yet: [from, commits]
    answered   \* the clients whose push the server answered

vars == <<repo, born, used, pc, known, inflight, answered>>

Symmetry == Permutations(Clients)

Range(s) == {s[i] : i \in DOMAIN s}

\* the elements of s that satisfy test, in order
Select(s, test(_)) == SelectSeq(s, test)

\* where id is in s (s holds it)
IndexOf(s, id) == CHOOSE i \in DOMAIN s : s[i] = id

\* [into] with the commits of [from] it lacks appended, in [from]'s order - or, without SkipHad, all of them
Replay(into, from) == into \o (IF SkipHad THEN Select(from, LAMBDA x : x \notin Range(into)) ELSE from)

TypeOK ==
    /\ repo \in [Repos -> Seq(Ids)]
    /\ born \in [Repos -> Seq(Ids)]
    /\ used \subseteq Ids
    /\ pc \in [Clients -> {"idle", "pushing", "waiting"}]
    /\ known \in [Clients -> SUBSET Ids]
    /\ answered \subseteq Clients

Init ==
    /\ repo = [r \in Repos |-> <<>>]
    /\ born = [r \in Repos |-> <<>>]
    /\ used = {}
    /\ pc = [c \in Clients |-> "idle"]
    /\ known = [c \in Clients |-> {}]
    /\ inflight = {}
    /\ answered = {}

-----------------------------------------------------------------------------

\* a commit: the server's any time between requests, a client's when it is not syncing (its lock)
Commit(r) ==
    /\ IF r = Server THEN TRUE ELSE pc[r] = "idle"
    /\ Ids \ used # {}
    /\ LET n == CHOOSE i \in Ids \ used : TRUE IN
         /\ repo' = [repo EXCEPT ![r] = Append(@, n)]
         /\ born' = [born EXCEPT ![r] = Append(@, n)]
         /\ used' = used \cup {n}
    /\ UNCHANGED <<pc, known, inflight, answered>>

\* PULL_COMMITS, and the client replaying the answer
Pull(c) ==
    /\ pc[c] = "idle"
    /\ repo' = [repo EXCEPT ![c] = Replay(@, Select(repo[Server], LAMBDA x : x \notin Range(repo[c])))]
    /\ UNCHANGED <<born, used, pc, known, inflight, answered>>

\* COMMIT_IDS: the first request of a push
AskIds(c) ==
    /\ pc[c] = "idle"
    /\ known' = [known EXCEPT ![c] = Range(repo[Server])]
    /\ pc' = [pc EXCEPT ![c] = "pushing"]
    /\ answered' = answered \ {c}
    /\ UNCHANGED <<repo, born, used, inflight>>

\* PUSH_COMMITS sent: the client's commits the server did not have when it asked
SendPush(c) ==
    /\ pc[c] = "pushing"
    /\ inflight' = inflight \cup {[from |-> c, commits |-> Select(repo[c], LAMBDA x : x \notin known[c])]}
    /\ pc' = [pc EXCEPT ![c] = "waiting"]
    /\ UNCHANGED <<repo, born, used, known, answered>>

\* the server takes a payload in, whenever it arrives, and answers
HandlePush(m) ==
    /\ m \in inflight
    /\ repo' = [repo EXCEPT ![Server] = Replay(@, m.commits)]
    /\ inflight' = inflight \ {m}
    /\ answered' = answered \cup {m.from}
    /\ UNCHANGED <<born, used, pc, known>>

\* the answer arrives
Done(c) ==
    /\ pc[c] = "waiting"
    /\ c \in answered
    /\ pc' = [pc EXCEPT ![c] = "idle"]
    /\ answered' = answered \ {c}
    /\ UNCHANGED <<repo, born, used, known, inflight>>

\* the client stops waiting or dies - its payload may still arrive - and may push again
GiveUp(c) ==
    /\ pc[c] # "idle"
    /\ pc' = [pc EXCEPT ![c] = "idle"]
    /\ UNCHANGED <<repo, born, used, known, inflight, answered>>

Next ==
    \/ \E r \in Repos : Commit(r)
    \/ \E c \in Clients : Pull(c) \/ AskIds(c) \/ SendPush(c) \/ Done(c) \/ GiveUp(c)
    \/ \E m \in inflight : HandlePush(m)

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
(* Properties *)

\* no repository holds a commit twice - also after a late payload and a retry carrying the same commits
NoDuplicates == \A r \in Repos : Len(repo[r]) = Cardinality(Range(repo[r]))

\* wherever a commit is, the commits made before it where it was made are too, and before it:
\* sync carries a repository's history whole and in order (FR-D3b/c)
CreationOrderKept ==
    \A o \in Repos, r \in Repos : \A j \in DOMAIN born[o] :
        born[o][j] \in Range(repo[r]) =>
            \A i \in 1..(j - 1) :
                /\ born[o][i] \in Range(repo[r])
                /\ IndexOf(repo[r], born[o][i]) < IndexOf(repo[r], born[o][j])

\* sync only appends: what a repository held stays where it was
AppendOnly == [][\A r \in Repos : SubSeq(repo'[r], 1, Len(repo[r])) = repo[r]]_vars

=============================================================================
