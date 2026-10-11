\version "2.20.0"
\language "deutsch"
\paper { tagline ="" }

#(set-default-paper-size "a4landscape")

global = {
  \key h \major
  \time 3/4
  \autoBeamOff
  \dynamicUp
  \phrasingSlurUp
}


SopranoVoice =  \relative c' {
  \global
  \clef "treble" 
}

AltoVoice =  \relative c' {
  \global
  \clef "treble" 
}



TenorVoice =  \relative c {
  \global 
  \clef "treble_8"
}



BassVoice =  \relative c {
  \global
  \clef "bass"
}

\score {
    <<  
      \new ChoirStaff <<
        
        \new Staff = "Sopran" <<
        \set Staff.instrumentName = "Sopran"
        \set Staff.shortInstrumentName = "S"
        \new Voice = "SopranoVoice" { \SopranoVoice }
        >>

        \new Staff = "Alt" <<
        \set Staff.instrumentName = "Alt"
        \set Staff.shortInstrumentName = "A"
        \new Voice = "AltoVoice" { \AltoVoice }
        >>

        \new Staff = "Tenor" <<
        \set Staff.instrumentName = "Tenor"
        \set Staff.shortInstrumentName = "T"
        \new Voice = "TenorVoice" { \TenorVoice }
        >>

        \new Staff  = "Bass" <<
        \set Staff.instrumentName = "Bass"
        \set Staff.shortInstrumentName = "B"
        \new Voice = "BassVoice" { \BassVoice }
        >>
      >>
    >>
    \layout{}
}