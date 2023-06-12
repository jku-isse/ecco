\version "2.20.0"
\language "deutsch"
\paper { tagline ="" }

#(set-default-paper-size "a4landscape")

\header {
  title = "Dieu! qu'il la fait bon regarder!"
  poet = "Charles d'Orléans"
  composer = "Claude Debussy"
  tagline = ""
}

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
  \tempo "Très modéré soutenu et expressif" 4 = 96
  r4 fis2^-\( gis8 gis8 fis8 fis8 gis8 gis8 
  fis2\) fis4\( gis8 gis8 cis8 cis8 ais8 fis8 
  fis4 fis4\) r4 \bar "|."
}

verseSopranoVoice =  \lyricmode {
  Dieu! qu'il la fait bon re -- gar -- der
  La gra -- ci -- eu -- se bonne et bel -- le;
}

AltoVoice =  \relative c' {
  \global
  \clef "treble" 
  r4 cis2^-\( 
  e8 \times 2/3  { dis16[ e16 dis16] } cis8 fis8 e8 
  \times 2/3  { dis16[ e16 dis16] }
  cis2\) cis4\( e8 e16[ fis16] gis8 
  \times 2/3  { fis16[ gis16 fis16] }
  e8 dis8 cis4 cis4\) r4 
}

verseAltoVoice =  \lyricmode {
  Dieu! qu'il la __ fait bon re -- gar -- der
  La gra -- ci -- eu -- se __ bonne et bel -- le;
}

TenorVoice =  \relative c {
  \global 
   \clef "treble_8"
   r4 ais'2-\( 
   cis8 h8 ais8 
   \times 2/3  { ais16[ h16 ais16] } cis8 
  \times 2/3  { h16[ cis16 h16] }
  ais2\) ais4\( h8 \times 2/3  { h16[ cis16 dis16] }
  e8 \times 2/3  { dis16[ e16 dis16] } cis8. h16 
  h8[ ais16 gis16] fis4\) r4 
}

verseTenorVoice =  \lyricmode {
  Dieu! qu'il la fait bon __ re -- gar -- der
  La gra -- ci -- eu -- se __ bonne et bel -- le;
}

BassVoice =  \relative c {
  \global
  \clef "bass"
  r4 fis2^-\( 
  cis8 cis8 fis8 fis8 cis8 dis16[ e16] 
  fis2\) fis4\( e8 dis8 cis4 ~ cis16 cis16 dis16 e16 
  fis4 ~ fis8[ e16 dis16] cis8 fis8\) 
}

verseBassVoice =  \lyricmode {
  Dieu! qu'il la fait bon re -- gar -- der
  La gra -- ci -- eu -- se bonne et bel -- _ le;
}

\score {
    <<  
      \new ChoirStaff <<
        
        \new Staff = "Sopran" <<
        \set Staff.instrumentName = "Sopran"
        \set Staff.shortInstrumentName = "S"
        \new Voice = "SopranoVoice" { \SopranoVoice }
        \new Lyrics \lyricsto "SopranoVoice"  \verseSopranoVoice 
        >>

        \new Staff = "Alt" <<
        \set Staff.instrumentName = "Alt"
        \set Staff.shortInstrumentName = "A"
        \new Voice = "AltoVoice" { \AltoVoice }
        \new Lyrics \lyricsto "AltoVoice"  \verseAltoVoice 
        >>

        \new Staff = "Tenor" <<
        \set Staff.instrumentName = "Tenor"
        \set Staff.shortInstrumentName = "T"
        \new Voice = "TenorVoice" { \TenorVoice }
        \new Lyrics \lyricsto "TenorVoice"  \verseTenorVoice 
        >>

        \new Staff  = "Bass" <<
        \set Staff.instrumentName = "Bass"
        \set Staff.shortInstrumentName = "B"
        \new Voice = "BassVoice" { \BassVoice }
        \new Lyrics \lyricsto "BassVoice"  \verseBassVoice
        >>
      >>
    >>
    \layout{}
}
