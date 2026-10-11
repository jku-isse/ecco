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
  r4 fis2 gis8 gis8 fis8 fis8 gis8 gis8 
  fis2 fis4 gis8 gis8 cis8 cis8 ais8 fis8 
  fis4 fis4 r4 \bar "|."
}



AltoVoice =  \relative c' {
  \global
  \clef "treble" 
  r4 cis2 
  e8 \times 2/3  { dis16[ e16 dis16] } cis8 fis8 e8 
  \times 2/3  { dis16[ e16 dis16] }
  cis2 cis4 e8 e16[ fis16] gis8 
  \times 2/3  { fis16[ gis16 fis16] }
  e8 dis8 cis4 cis4 r4 
}



TenorVoice =  \relative c {
  \global 
   \clef "treble_8"
   r4 ais'2 
   cis8 h8 ais8 
   \times 2/3  { ais16[ h16 ais16] } cis8 
  \times 2/3  { h16[ cis16 h16] }
  ais2 ais4 h8 \times 2/3  { h16[ cis16 dis16] }
  e8 \times 2/3  { dis16[ e16 dis16] } cis8. h16 
  h8[ ais16 gis16] fis4 r4 
}



BassVoice =  \relative c {
  \global
  \clef "bass"
  r4 fis2 
  cis8 cis8 fis8 fis8 cis8 dis16[ e16] 
  fis2 fis4 e8 dis8 cis4  cis16 cis16 dis16 e16 
  fis4  fis8[ e16 dis16] cis8 fis8 
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
