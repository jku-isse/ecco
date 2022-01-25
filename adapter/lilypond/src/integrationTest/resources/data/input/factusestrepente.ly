\version "2.19.80"
%\include "snippets/editorial-tools/merge-rests-engraver/module.ily"

\header {
title = "Factus est repente"
composer = "Balduin Sulzer"
subtitle = "Pfingstantiphon für 2 Soprane, 2 Tenöre und 2 Bässe a capella"
poet = "In memoriam Joseph Kronsteiner"
dedication = "Gewidmet dem Vokalensemble Voices"
tagline = ""
}
sfpp = #(make-dynamic-script "sfpp")




global = {
 \key c \major
 
 \dynamicUp
 \override Hairpin.to-barline = ##f
 %\override Staff.BarLine.hair-thickness = #0.25
 \autoBeamOff
}


sopranoOneVoice = \relative c'' {
 \global
 
 % Music follows here.
 \tempo "Tempo giusto" 4 = 92
 R1*3 
 b,8\mf r fis'^>\sf r b,\mf r fis'^>\sf r fis^>\sf r b,\mf r b r fis'^>\sf fis^>\sf b,\mf r fis'^>\sf r b,\mf r fis'^>\sf r fis^>\sf r b,\mf r b r fis'^>\sf fis^>\sf
 gis8.\p gis16 gis8. gis16 gis8. gis16 gis8. gis16 
 
 gis8 dis16 fis gis8 dis16\([ fis]\) gis8 dis16[\( fis]\) gis dis dis\([ fis]\) gis\([ b]\) b\([ cis]\) dis8^> [ 
 b16\( cis] dis8[\) b16\( cis] dis8[\) b16\( cis] dis[\) cis-. cis-. dis-.] dis-.[ cis-. cis-. dis-.] 
 dis-.[ cis-. cis-. dis-.] dis[\( cis dis8]\) 
 
 \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { dis^>\sf b dis^>\sf }
 \tuplet 3/2 { dis8 b16 b dis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { dis8^>\sf b dis^>\sf } \tuplet 3/2 { dis8^> b16 b dis8 }
 \tuplet 3/2 { e[(\p dis)] cis } \tuplet 3/2 { dis e dis } \tuplet 3/2 { gis,4( dis'8 } \tuplet 3/2 { b4 e,8 } \tuplet 3/2 { gis[ e cis'] } \tuplet 3/2 {gis4) gis8 ~ } 
   gis2.  ~  \tuplet 3/2 {  gis8 r r } 
 \tuplet 3/2 { e'8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { dis^>\sf b dis^>\sf }
 \tuplet 3/2 { dis8 b16 b dis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { e8.^>\sf cis16 cis8 } \tuplet 3/2 { dis8^>\sf b dis^>\sf } \tuplet 3/2 { dis8^> b16 b dis8^> } \tuplet 3/2 { dis8^> b16 b dis8^> } r4
 e4.\fp^> e8 dis4 dis8 dis fis[( cis] dis4. cis8) dis([ fis]) e4( fis4.) e8 e([ dis]) dis4( gis) gis2 ~ gis4 ~ gis8 r8 r4
 e fis2 fis4 gis a2 gis4. fis8 gis2 fis4 gis a2 a gis1 ~ gis ~ gis4 r8 g!8\pp fis4 f! e r8 ees8( d4 ees4 ~ ees8[ d] ees4 d8[ ees d ees] ~ ees4 d) d2\> ~ d4\! r r2
 \tuplet 3/2 { b8.^>\p b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { b8.^>\f b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { e8.^>\sff cis16 cis8 } \tuplet 3/2 { e8.^>\sff cis16 cis8 } 
 \tuplet 3/2 { e8.^>\sff cis16 cis8 } \tuplet 3/2 { dis^>\sff b dis^>\sf }
 \tuplet 3/2 { dis8^>\sff b16 b dis8 } \tuplet 3/2 { e8.^>\sff cis16 cis8 } \tuplet 3/2 { e8.^>\sff cis16 cis8 } 
 \tuplet 3/2 { e8.^>\sff cis16 cis8 }  \tuplet 3/2 { dis8^>\sff b dis^>\sf } \tuplet 3/2 { dis8^> b16 b dis8\sff^> } r2
 b'8\ff r dis, r b' r dis, r R1 b'8\ff r dis, r b' r dis, r
  \tuplet 3/2 { dis8([\f cis b] cis[ dis]) cis } \tuplet 3/2 {  b4.( dis,) } \tuplet 3/2 {  fis ~ fis8 r4 } \tuplet 3/2 { gis8([\p ais gis] fis[ dis]) cis }  b4.( cis8) cis2( ~ cis2.\> dis8[ b] cis2 dis\fermata)\!
 \bar "|."
}

verseSopranoOneVoice = \lyricmode {
 % Lyrics follow here.
 so -- nus, so -- nus -- nus, so -- nus, so -- nus, so -- nus, so -- nus -- nus, so -- nus, so -- nus,
 tam -- quam, tam -- quam, tam -- quam, tam -- quam, 
 tam -- quam, ad -- ve -- ni -- en -- tis  __ Spi -- ri -- tus __ 
 ve -- he -- men -- _ _ _ _ _ tis, __
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 U -- bi e -- rant se -- den -- tes. __
 Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, ha -- ha -- ha -- ha!
 Et re -- ple -- ti sunt om -- nes Spi -- ri -- tu __ Sanc -- to __ 
 lo -- quen -- tes mag -- na -- li -- a De -- i, mag -- na -- li -- a, __ mag -- na -- li -- a De -- i. __
 Al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 Al -- le -- lu -- ja, al -- le -- lu -- ja,
 al -- le -- lu -- ja, __ al -- le -- lu -- ja. __
 }

sopranoTwoVoice = \relative c'' {
 \global
 
 % Music follows here.
 R1*3 
 b,8\mf r cis^>\sf r b\mf r cis^>\sf r cis^>\sf r b\mf r b r cis^>\sf cis^>\sf b\mf r cis^>\sf r b\mf r cis^>\sf r cis^>\sf r b\mf r b r cis^>\sf cis^>\sf
 gis'8.\p gis16 gis8. gis16 gis8. gis16 gis8. gis16 
 
 gis8 dis16 fis gis8 dis16\([ fis]\) gis8 dis16[\( fis]\) gis dis dis\([ fis]\) gis\([ b]\) b\([ cis]\) dis8^> [ 
 b16\( cis] dis8[\) b16\( cis] dis8[\) b16\( cis] dis[\) cis-. cis-. dis-.] dis-.[ cis-. cis-. dis-.] 
 dis-.[ cis-. cis-. dis-.] dis[\( cis dis8]\)
 
 \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { b^>\sf gis b^>\sf }
 \tuplet 3/2 { b8 gis16 gis b8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { b8^>\sf gis b^>\sf } \tuplet 3/2 { b8^> gis16 gis b8 }
 \tuplet 3/2 { cis[(\p b)] a } \tuplet 3/2 { b cis b } \tuplet 3/2 { e,4( b'8 } \tuplet 3/2 { gis4 cis,8 } \tuplet 3/2 { e[ cis a'] } \tuplet 3/2 {e4) e8 ~} 
  e2. ~ \tuplet 3/2 {  e8 r r }
 \tuplet 3/2 { cis'8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { b^>\sf gis b^>\sf }
 \tuplet 3/2 { b8 gis16 gis b8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { cis8.^>\sf gis16 gis8 } \tuplet 3/2 { b8^>\sf gis b^>\sf } \tuplet 3/2 { b8^> gis16 gis b8^> } \tuplet 3/2 { b8^> gis16 gis b8^> } r4
 cis4.\fp^> cis8 b4 b8 b cis([ a] b4. a8) b4 cis4( dis4.) cis8 cis([ b]) b2 cis2 ~ cis4 ~ cis8 r8 r4
 cis4 dis2 dis4 cis d!( cis) b4. a8 cis2 ais4 cis d!2 d cis1 ~ cis ~ cis4 r8 c!8\pp b4 bes a r8 aes( g4 aes ~ aes8[ g] aes4 g8[ aes g aes] ~ aes4 g) g2\> ~ g4\! r4 r2
 \tuplet 3/2 { b8.^>\p b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { b8.^>\f b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { cis8.^>\sff gis16 gis8 } \tuplet 3/2 { cis8.^>\sff gis16 gis8 } \tuplet 3/2 { cis8.^>\sff gis16 gis8 } \tuplet 3/2 { b^>\sff gis b^>\sf }
 \tuplet 3/2 { b8^>\sff gis16 gis b8 } \tuplet 3/2 { cis8.^>\sff gis16 gis8 } \tuplet 3/2 { cis8.^>\sff gis16 gis8 } \tuplet 3/2 { cis8.^>\sff gis16 gis8 } \tuplet 3/2 { b8^>\sff gis b^>\sf } \tuplet 3/2 { b8^> gis16 gis b8\sff^> }  \tuplet 3/2 { gis8.\mf gis16 gis8 }  \tuplet 3/2 { gis gis gis }
dis'8\ff r dis r dis r dis r  \tuplet 3/2 {gis,8.^>\mf gis16 gis8 }  \tuplet 3/2 { gis8.^> gis16 gis8 }  \tuplet 3/2 {gis8.^> gis16 gis8 }  \tuplet 3/2 {gis8.^> gis16 gis8 }  dis'8\ff r dis r dis r dis r
  \tuplet 3/2 { dis8([\f cis b] cis[ dis]) cis } \tuplet 3/2 {  b4.( dis,)} \tuplet 3/2 {   fis ~ fis8 r4 } \tuplet 3/2 { gis8([\p ais gis] fis[ dis]) cis }  b4.( cis8) cis2~ cis1\> ~ cis \fermata\! 
 \bar "|."
}

verseSopranoTwoVoice = \lyricmode {
 % Lyrics follow here.
 so -- nus, so -- nus -- nus, so -- nus, so -- nus, so -- nus, so -- nus -- nus, so -- nus, so -- nus,
 tam -- quam, tam -- quam, tam -- quam, tam -- quam, 
 tam -- quam, ad -- ve -- ni -- en -- tis __ Spi -- ri -- tus ve -- he -- men -- _ _ _ _ _ tis, __
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 U -- bi e -- rant se -- den -- tes. __
 Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, ha -- ha -- ha -- ha!
 Et re -- ple -- ti sunt om -- nes Spi -- ri -- tu __ Sanc -- to __ 
 lo -- quen -- tes mag -- na -- li -- a De -- i, mag -- na -- li -- a, __ mag -- na -- li -- a De -- i. __
 Al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, al -- le -- lu, lu -- ja -- ha,
 al -- le -- lu -- ja, al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja,
 al -- le -- lu -- ja, __ al -- le -- lu -- ja. __
 
}

tenorOneVoice = \relative c' {
 \global
 
 % Music follows here.
 b4\p b b8. b16 b4 b b b8. b16 b4 b8.\< b16 b8. b16 b8 b16 b b8 b 
 b8\mf r fis'^>\sf r b,\mf r fis'^>\sf r fis^>\sf r b,\mf r b r fis'^>\sf fis^>\sf b,\mf r fis'^>\sf r b,\mf r fis'^>\sf r fis^>\sf r b,\mf r b r fis'^>\sf fis^>\sf
 gis,8.\p gis16 gis8. gis16 gis8. gis16 gis8. gis16 
 gis8 dis16 fis gis8 dis16\([ fis]\) gis8 dis16[\( fis]\) gis dis dis\([ fis]\) gis\([ b]\) b\([ cis]\) dis8^> [ 
 b16\( cis] dis8[\) b16\( cis] dis8[\) b16\( cis] dis[\) cis-. cis-. dis-.] dis-.[ cis-. cis-. dis-.] 
 dis-.[ cis-. cis-. dis-.] dis[\( cis dis8]\)
 
 \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis^>\sf dis gis^>\sf }
 \tuplet 3/2 { gis8 dis16 dis gis8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8^>\sf dis gis^>\sf } \tuplet 3/2 { gis8^> dis16 dis gis8 }
 r2 R1*2
 \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis^>\sf dis gis^>\sf }
 \tuplet 3/2 { gis8 dis16 dis gis8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8.^>\sf e16 e8 } \tuplet 3/2 { gis8^>\sf dis gis^>\sf } \tuplet 3/2 { gis8^> dis16 dis gis8^> } \tuplet 3/2 { gis8^> dis16 dis gis8^> } r4
 a4.\fp^> a8 gis4 gis8 gis a([ fis] gis4. fis8) gis4 a4( b4.) a8 a([ gis]) gis4( e) e2 ~ e4 ~ e8 r8 r4
 gis4 ais2 ais4 e fis2 e4. d8 e2 dis4 e fis2 fis e1 ~ e ~ e4 r8 ees8\pp d4 des c r8 b( ais4 b ~ b8[ ais] b4 ais8[ b ais b] ~ b4 ais) ais2\> ~ ais4\! r4 r2 R1
 \tuplet 3/2 { b8.^>\f b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { gis'8.^>\sff e16 e8 } \tuplet 3/2 { gis8.^>\sff e16 e8 } \tuplet 3/2 { gis8.^>\sff e16 e8 } \tuplet 3/2 { gis^>\sff dis gis^>\sf }
 \tuplet 3/2 { gis8^>\sff dis16 dis gis8 } \tuplet 3/2 { gis8.^>\sff e16 e8 } \tuplet 3/2 { gis8.^>\sff e16 e8 } \tuplet 3/2 { gis8.^>\sff e16 e8 } \tuplet 3/2 { gis8^>\sff dis gis^>\sf } \tuplet 3/2 { gis8^> dis16 dis gis8^>\sff } r2
  gis8\ff r ais r gis r ais r R1 gis8\ff r ais r gis r ais r R1 r2 \tuplet 3/2 { gis,8([\p ais gis] fis[ dis]) cis } b2.( b'4) b1\> ~ b\fermata\!
 
 
 \bar "|." 
}

verseTenorOneVoice = \lyricmode {
 % Lyrics follow here.
 Fac -- tus, fac -- tus est, fac -- tus, fac -- tus est, fac -- tus est re -- pen -- te de cœ -- lo
 so -- nus, so -- nus -- nus, so -- nus, so -- nus, so -- nus, so -- nus -- nus, so -- nus, so -- nus,
 tam -- quam, tam -- quam, tam -- quam, tam -- quam, 
 tam -- quam, ad -- ve -- ni -- en -- tis __ Spi -- ri -- tus ve -- he -- men -- _ _ _ _ _ tis, __
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, ha -- ha -- ha -- ha!
 Et re -- ple -- ti sunt om -- nes Spi -- ri -- tu __ Sanc -- to __ 
 lo -- quen -- tes mag -- na -- li -- a De -- i, mag -- na -- li -- a, __ mag -- na -- li -- a De -- i. __
 Al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 Al -- le -- lu -- ja, al -- le -- lu -- ja,
 al -- le -- lu -- ja. __
 
}

tenorTwoVoice = \relative c' {
 \global
 
 % Music follows here.
 b4\p b b8. b16 b4 b b b8. b16 b4 b8.\< b16 b8. b16 b8 b16 b b8 b 
 b8\mf r cis^>\sf r b\mf r cis^>\sf r cis^>\sf r b\mf r b r cis^>\sf cis^>\sf b\mf r cis^>\sf r b\mf r cis^>\sf r cis^>\sf r b\mf r b r cis^>\sf cis^>\sf
 gis8.\p gis16 gis8. gis16 gis8. gis16 gis8. gis16 
 gis8 dis16 fis gis8 dis16\([ fis]\) gis8 dis16[\( fis]\) gis dis dis\([ fis]\) gis\([ b]\) b\([ cis]\) dis8^> [ 
 b16\( cis] dis8[\) b16\( cis] dis8[\) b16\( cis] dis[\) cis-. cis-. dis-.] dis-.[ cis-. cis-. dis-.] 
 dis-.[ cis-. cis-. dis-.] dis[\( cis dis8]\)
 
 \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { gis,^>\sf cis e } \tuplet 3/2 { gis,^>\sf cis e } \tuplet 3/2 { dis^>\sf gis dis^>\sf }
 \tuplet 3/2 { dis8 gis16 gis dis8 } \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { dis8^>\sf gis dis^>\sf } \tuplet 3/2 { dis8^> gis16 gis dis8 }
 r2 R1*2
 \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { gis,^>\sf cis e } \tuplet 3/2 { gis,^>\sf cis e } \tuplet 3/2 { dis^>\sf gis dis^>\sf }
 \tuplet 3/2 { dis8 gis16 gis dis8 } \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { gis,8^>\sf cis e } \tuplet 3/2 { dis8^>\sf gis dis^>\sf } \tuplet 3/2 { dis8^> gis16 gis dis8^> } \tuplet 3/2 { dis8^> gis16 gis dis8^> } 
 dis4\fp^> ~ dis8([ b]) b4 a a8 a gis4.( e'8 b4) a b4.( dis8) b4 a b8([ cis b cis] dis[ e dis cis] e[ gis]) gis4 r4
 e4 e( fis) fis cis8([ b]) b2 cis8[ d!] d4 ~ d cis8.([ e16]) dis4. d8 b4( cis8[ d!]) d4 d8[ e] fis16-.[ r8 e16-.] fis[ r8 e16-.] fis-.[ r8 e16-.] dis-.[ r8 e16-.] fis-.[ r8 e16-.] e-.[ r8 fis16-.] dis-.[ r8 cis16-.] cis8([-. dis])-. ais([ b] ais2.)\sfpp ~ ais1\pp ~ ais ~ ais ~ ais\< ~ ais\!
 \tuplet 3/2 { b8.^>\f b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { gis8^>\sff cis e } \tuplet 3/2 { gis,^>\sff cis e } \tuplet 3/2 { gis,^>\sff cis e } \tuplet 3/2 { dis^>\sff gis dis^>\sf }
 \tuplet 3/2 { dis8^>\sff gis16 gis dis8 } \tuplet 3/2 { gis,8^>\sff cis e } \tuplet 3/2 { gis,8^>\sff cis e } \tuplet 3/2 { gis,8^>\sff cis e } \tuplet 3/2 { dis8^>\sff gis dis^>\sf } \tuplet 3/2 { dis8^> gis16 gis dis8^>\sff }  \tuplet 3/2 { gis,8.\mf gis16 gis8}  \tuplet 3/2 { gis gis gis }
  dis'8\ff r fis r dis r fis r  \tuplet 3/2 { gis,8.^>\mf gis16  gis8 }   \tuplet 3/2 { gis8.^> gis16  gis8 }   \tuplet 3/2 { gis8.^> gis16 gis8 }   \tuplet 3/2 { gis8.^> gis16 gis8 }   dis'8\ff r fis r dis r fis r
 R1 r2 \tuplet 3/2 { gis,8([\p ais gis] fis[ dis]) cis } b4( dis2 gis8[ ais]) ais1\> ~ ais\fermata\!
 
}

verseTenorTwoVoice = \lyricmode {
 % Lyrics follow here.
 Fac -- tus, fac -- tus est, fac -- tus, fac -- tus est, fac -- tus est re -- pen -- te de cœ -- lo
 so -- nus, so -- nus -- nus, so -- nus, so -- nus, so -- nus, so -- nus -- nus, so -- nus, so -- nus,
 tam -- quam, tam -- quam, tam -- quam, tam -- quam, 
 tam -- quam, ad -- ve -- ni -- en -- tis __ Spi -- ri -- tus ve -- he -- men -- _ _ _ _ _ tis, __
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, ha -- ha -- ha -- ha!
 Et __ re -- ple -- ti sunt om -- nes Spi -- ri -- tu Sanc -- to 
 lo -- quen -- tes mag -- na -- li -- a __ De -- i, mag -- na -- li -- a __  
 De -- _ _ _ _ _ _ _ i. __ 
 Al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, al -- le -- lu, lu -- ja -- ha,
 al -- le -- lu -- ja, al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja,
 al -- le -- lu -- ja. __
}

bassOneVoice = \relative c {
 \global
 
 % Music follows here.
 b4\p r b r4 b4 r b r4 b8\< b b b b b16 b b8 b 
 b8\mf r fis'^>\sf r b,\mf r fis'^>\sf r fis^>\sf r b,\mf r b r fis'^>\sf fis^>\sf b,\mf r fis'^>\sf r b,\mf r fis'^>\sf r fis^>\sf r b,\mf r b r fis'^>\sf fis^>\sf
 gis8.\p gis,16 gis8. gis16 gis8. gis16 gis8. gis16 
 gis8 dis16 fis gis8 dis16\([ fis]\) gis8 dis16[\( fis]\) gis dis dis\([ fis]\) gis\([ b]\) b\([ cis]\) dis8^> [ 
 b16\( cis] dis8[\) b16\( cis] dis8[\) b16\( cis] dis[\) cis-. cis-. dis-.] dis-.[ cis-. cis-. dis-.] 
 dis-.[ cis-. cis-. dis-.] dis[\( cis dis8]\)
 \tuplet 3/2 { e8^>\sf gis cis } \tuplet 3/2 { e,^>\sf gis cis } \tuplet 3/2 { e,^>\sf gis cis } \tuplet 3/2 { b^>\sf dis b^>\sf }
 \tuplet 3/2 { b8 dis16 dis b8 } \tuplet 3/2 { e,8^>\sf gis cis } \tuplet 3/2 { e,8^>\sf gis cis } \tuplet 3/2 { e,8^>\sf gis cis } \tuplet 3/2 { b8^>\sf dis b^>\sf } \tuplet 3/2 { b8^> dis16 dis b8 }
 r2 r2. \tuplet 3/2 {  r8 r gis\p } \tuplet 3/2 {  gis4.(  cis  gis)} \tuplet 3/2 {  gis8 r r  }
 \tuplet 3/2 { e8^>\sf gis cis } \tuplet 3/2 { e,^>\sf gis cis } \tuplet 3/2 { e,^>\sf gis cis } \tuplet 3/2 { b^>\sf dis b^>\sf }
 \tuplet 3/2 { b8 dis16 dis b8 } \tuplet 3/2 { e,8^>\sf gis cis } \tuplet 3/2 { e,8^>\sf gis cis } \tuplet 3/2 { e,8^>\sf gis cis } \tuplet 3/2 { b8^>\sf dis b } \tuplet 3/2 { b8^> dis16 dis b8^> } \tuplet 3/2 { b8^> dis16 dis b8^> } 
 b4\fp^> ~ b8([ gis]) gis4 fis fis8 fis e4.( b'8 gis4) fis gis4.( b8) gis4 fis gis8([ a? gis a] b[ cis b a] cis[ e]) e4 r4
 cis4 cis( dis) dis gis, fis2 gis4 a ~ a gis8.([ cis16]) ais4. gis8 fis4( gis8[ a!]) a4 a8[ b] cis16-.[ r8 b16-.] cis-.[ r8 b16-.] cis[ r8-. b16-.] ais-.[ r8 b16-.] cis16-.[ r8 b16-.] b-.[ r8 cis16-.] ais-.[ r8 gis16-.] gis4-. fis1\sfpp^> ~ fis\pp ~ fis ~ fis ~ fis\< ~ fis\!
 \tuplet 3/2 { b8.^>\f b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { e,8^>\sff gis cis } \tuplet 3/2 { e,^>\sff gis cis } \tuplet 3/2 { e,^>\sff gis cis } \tuplet 3/2 { b^>\sff dis b^>\sf }
 \tuplet 3/2 { b8^>\sff dis16 dis b8 } \tuplet 3/2 { e,8^>\sff gis cis } \tuplet 3/2 { e,8^>\sff gis cis } \tuplet 3/2 { e,8^>\sff gis cis } \tuplet 3/2 { b8^>\sff dis b^>\sf } \tuplet 3/2 { b8^> dis16 dis b8^>\sff } r2
  b8\ff r dis r b r dis r R1 b8\ff r dis r b r dis r
 R1 r2 \tuplet 3/2 { gis,8([\p ais gis] fis[ dis]) cis } b4( dis2 gis4) gis1\> ~ gis\fermata\!
 \bar "|." 
}

verseBassOneVoice = \lyricmode {
 % Lyrics follow here.
 Fac -- tus, fac -- tus, fac -- tus est re -- pen -- te de cœ -- lo
 so -- nus, so -- nus -- nus, so -- nus, so -- nus, so -- nus, so -- nus -- nus, so -- nus, so -- nus,
 tam -- quam, tam -- quam, tam -- quam, tam -- quam, 
 tam -- quam, ad -- ve -- ni -- en -- tis __ Spi -- ri -- tus ve -- he -- men -- _ _ _ _ _ tis, __
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 se -- den -- tes.
 Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, ha -- ha -- ha -- ha!
 Et __ re -- ple -- ti sunt om -- nes Spi -- ri -- tu Sanc -- to 
 lo -- quen -- tes mag -- na -- li -- a __ De -- i, mag -- na -- li -- a __  
 De -- _ _ _ _ _ _ _  i. __ 
 Al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 Al -- le -- lu -- ja, al -- le -- lu -- ja,
 al -- le -- lu -- ja. __
 
 
}

bassTwoVoice = \relative c {
 \global
 
 % Music follows here.
 b4\p r b r4 b4 r b r4 b8\< b b b b b16 b b8 b 
 b8\mf r cis^>\sf r b\mf r cis^>\sf r cis^>\sf r b\mf r b r cis^>\sf cis^>\sf b\mf r cis^>\sf r b\mf r cis^>\sf r cis^>\sf r b\mf r b r cis^>\sf cis^>\sf
 gis8.\p gis16 gis8. gis16 gis8. gis16 gis8. gis16 
 gis8 dis16 fis gis8 dis16\([ fis]\) gis8 dis16[\( fis]\) gis dis dis\([ fis]\) gis\([ b]\) b\([ cis]\) dis8^> [ 
 b16\( cis] dis8[\) b16\( cis] dis8[\) b16\( cis] dis[\) cis-. cis-. dis-.] dis-.[ cis-. cis-. dis-.] 
 dis-.[ cis-. cis-. dis-.] dis[\( cis dis8]\)
 \tuplet 3/2 { cis8^>\sf e gis } \tuplet 3/2 { cis,^>\sf e gis } \tuplet 3/2 { cis,^>\sf e gis} \tuplet 3/2 { gis^>\sf b gis^>\sf }
 \tuplet 3/2 { gis8 b16 b gis8 } \tuplet 3/2 { cis,8^>\sf e gis } \tuplet 3/2 { cis,8^>\sf e gis } \tuplet 3/2 {cis,8^>\sf e gis } \tuplet 3/2 { gis8^>\sf b gis^>\sf } \tuplet 3/2 { gis8^> b16 b gis8 }
 r2 r2. \tuplet 3/2 { r8 r e\p } \tuplet 3/2 {  e4.( a e) } \tuplet 3/2 {  e8 r r } 
 \tuplet 3/2 { cis8^>\sf e gis } \tuplet 3/2 { cis,^>\sf e gis } \tuplet 3/2 { cis,^>\sf e gis} \tuplet 3/2 { gis^>\sf b gis^>\sf }
 \tuplet 3/2 { gis8 b16 b gis8 } \tuplet 3/2 { cis,8^>\sf e gis } \tuplet 3/2 { cis,8^>\sf e gis } \tuplet 3/2 {cis,8^>\sf e gis } \tuplet 3/2 { gis8^>\sf b gis } \tuplet 3/2 { gis8^> b16 b gis8^> } \tuplet 3/2 { gis8^> b16 b gis8^> } 
 gis4\fp^> ~ gis8([ dis]) dis([ e]) cis([ dis]) cis cis b4.( gis'8 e4) cis dis4.( gis8) dis4 cis dis8([ e dis e] fis[ gis fis e] gis[ cis]) cis4 r4
 gis4 gis( ais) ais e d?( e) e fis ~ fis e8.([ gis16]) fis4. e8 d!4( e8[ fis]) fis4 fis8[ gis] ais16-.[ r8 gis16-.] ais-.[ r8 gis16-.] ais-.[ r8 gis16-.] fis-.[ r8 gis16-.] ais-.[ r8 gis16-.] gis-.[ r8 ais16-.] fis-.[ r8 e16-.] e4-. dis1\sfpp^> ~ dis\pp ~ dis ~ dis ~ dis\< ~ dis\!
 \tuplet 3/2 { b'8.^>\f b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } \tuplet 3/2 { b8.^> b16 b8 } 
 \tuplet 3/2 { cis,8^>\sff e gis } \tuplet 3/2 { cis,^>\sff e gis } \tuplet 3/2 { cis,^>\sff e gis} \tuplet 3/2 { gis^>\sff b gis^>\sf }
 \tuplet 3/2 { gis8^>\sff b16 b gis8 } \tuplet 3/2 { cis,8^>\sff e gis } \tuplet 3/2 { cis,8^>\sff e gis } \tuplet 3/2 {cis,8^>\sff e gis } \tuplet 3/2 { gis8^>\sff b gis^>\sf } \tuplet 3/2 { gis8^> b16 b gis8^>\sff } r2
  gis8\ff r dis' r gis, r dis' r R1 gis,8\ff r dis' r gis, r dis' r
 R1 r2 \tuplet 3/2 { gis,8([\p ais gis] fis[ dis]) cis } b4( gis) fis2 fis1\> ~ fis\fermata\!
 \bar "|." 
}

verseBassTwoVoice = \lyricmode {
 % Lyrics follow here.
 Fac -- tus, fac -- tus, fac -- tus est re -- pen -- te de cœ -- lo
 so -- nus, so -- nus -- nus, so -- nus, so -- nus, so -- nus, so -- nus -- nus, so -- nus, so -- nus,
 tam -- quam, tam -- quam, tam -- quam, tam -- quam, 
 tam -- quam, ad -- ve -- ni -- en -- tis __ Spi -- ri -- tus ve -- he -- men -- _ _ _ _ _ tis, __
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 se -- den -- tes.
 Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 Ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha, ha -- ha -- ha -- ha!
 Et __ re -- ple -- ti sunt om -- nes Spi -- ri -- tu Sanc -- to 
 lo -- quen -- tes mag -- na -- li -- a __ De -- i, mag -- na -- li -- a __  
 De -- _ _ _ _ _ _ _  i. __ 
 Al -- le -- lu, al -- le -- lu, al -- le -- lu, al -- le -- lu, 
 al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha!
 ha -- ha -- ha -- ha! Al -- le -- lu, al -- le -- lu, al -- le -- lu -- ja -- _ ha, ha -- ha -- ha -- ha!
 Al -- le -- lu -- ja, al -- le -- lu -- ja,
 al -- le -- lu -- _ ja. __
}

sopranoOneVoicePart = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Sopran 1"
 shortInstrumentName = "S1"
 %midiInstrument = "string ensemble 1"
} << \sopranoOneVoice 
  %\new Dynamics = Dsone  \with { belowAboveContext = "sopranoOneVoicePart" } \dyn
>>
\addlyrics { \verseSopranoOneVoice }

sopranoTwoVoicePart = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Sopran 2"
 shortInstrumentName = "S2"
 %midiInstrument = "string ensemble 1"
} { \sopranoTwoVoice }
\addlyrics { \verseSopranoTwoVoice }

tenorOneVoicePart = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Tenor 1"
 shortInstrumentName = "T1"
 %midiInstrument = "string ensemble 1"
} { \clef "treble_8" \tenorOneVoice }
\addlyrics { \verseTenorOneVoice }

tenorTwoVoicePart = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Tenor 2"
 shortInstrumentName = "T2"
 %midiInstrument = "string ensemble 1"
} { \clef "treble_8" \tenorTwoVoice }
\addlyrics { \verseTenorTwoVoice }

bassOneVoicePart = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Bass 1"
 shortInstrumentName = "B1"
 %midiInstrument = "string ensemble 1"
} { \clef bass \bassOneVoice }
\addlyrics { \verseBassOneVoice }

bassTwoVoicePart = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Bass 2"
 shortInstrumentName = "B2"
 %midiInstrument = "string ensemble 1"
} { \clef bass \bassTwoVoice }
\addlyrics { \verseBassTwoVoice }

%{  %}
scoreA = { \new ChoirStaff << \transpose f f {
  <<
    \sopranoOneVoicePart
    \sopranoTwoVoicePart
    \tenorOneVoicePart
    \tenorTwoVoicePart
    \bassOneVoicePart
    \bassTwoVoicePart 
    
    >>
         } >>
}
\score { \scoreA 
  %\include "layout.ily"
}


sopranoOneVoicePsalm = \relative c'' {
 \time 6/8 \autoBeamOff
 
 % Music follows here.
 \tempo "Psalm" \dynamicUp
  ais8^.\pp ais8^. ais8^. ais8^. ais8^. ais8^> ~ais ais16^. ais^. ais8^. ais8^. ais8^. ais8^.
  ais\mf dis^. fis^. ais^. fis^. dis^. ais'^. fis^. dis^. ais'^.([ gis]) ais^. b8. ais16^. gis8^. ais4( dis,8) dis4. ~dis8 r4
  dis8^>\pp dis^. dis^. dis^> dis^. dis^> ~dis dis16^. dis16^.  dis16^.  dis16^.  dis8^. dis8^- r
  ais8 dis8([ fis]) ais,8([ dis]) fis^. ais,8 dis8([ fis]) gis8([ ais gis] fis) gis^. dis4 r4 ais8 dis8^. fis^. ais, dis8([ fis])
  ais,8([ dis]) fis^. gis32^> gis^. gis16^. ais8^. gis^. gis32^> gis^. gis16^. ais8^. gis8^. gis32^> gis^. gis16^. ais8^. gis^.
  ais8([ b ais] gis[ ais b] dis,4 fis8) dis4 r8 dis4(\pp fis8) dis4 r8 dis4(\p fis8) fis( dis4 ~dis8) r4
  gis,4(\p ais8 gis4 gis'8) dis4. ~dis8 r4 gis,8([\mf ais b] ais[ gis ais] gis4. gis'4. ~gis4 dis8) dis4. ~dis8 r4 dis8^.\p dis8^. dis8^. dis8^. dis8^. dis8^>
~dis dis16^. dis^. dis8^. dis4.^>~dis2.\> ~dis8 r4 r4.\!
  \bar "|." 
}

verseSopranoOneVoicePsalm = \lyricmode {
 % Lyrics follow here.
 Glo -- ri -- a, glo -- ri -- a, __ glo -- ri -- a, glo -- ri -- a, glo -- ri -- a Pa -- tri et ﬁ -- li -- o et __ spi -- ri -- tu -- i sanc -- to; __
 sanc -- to, sanc -- to, sanc -- to, __ sanc -- to, sanc -- to, sanc -- to, si -- cut e -- rat in prin -- ci -- pi -- o et nunc et sem -- per __
et __ in sæ -- cu -- la sæ -- cu, sæ -- cu -- la sæ -- cu, sæ -- cu -- la sæ -- cu -- lo -- rum, lo -- rum, lo -- rum, __
a -- men,  a -- men, a -- men, a -- men, a -- men, a -- men, a -- men. __
}
 
 sopranoTwoVoicePsalm = \relative c' {
\time 6/8 \autoBeamOff
 
 % Music follows here.
 \tempo "Psalm" \dynamicUp
  fis8^.\pp fis8^. fis8^. fis8^. fis8^. fis8^> ~fis fis16^. fis^. fis8^. fis8^. fis8^. fis8^.
  fis\mf b^. dis^. fis^. dis^. b^. fis'^. dis^. b^. fis'^.([ e]) fis^. gis8. fis16^. e8^. fis4( b,8) b4. ~b8 r4
  b8^>\pp b^. b^. b^> b^. b^> ~b b16^. b16^.  b16^.  b16^.  b8^. b8^- r
  fis8\mf b8([ dis]) fis,8([ b]) dis^. fis,8 b8([ dis]) e8([ fis e] dis) e^. b4 r4 fis8 b8^. dis^. fis, b8([ dis])
  fis,8([ b]) dis^. e32^> e^. e16^. fis8^. e^. e32^> e^. e16^. fis8^. e8^. e32^> e^. e16^. fis8^. e^.
  fis8([ gis fis] e[ fis gis] b,4 dis8) b4 r8 b4(\pp dis8) b4 r8 b4(\p dis8) dis( b4 ~b8) r4
  e,4(\p fis8 e4 e'8) b4. ~b8 r4 e,8([\mf fis gis] fis[ e fis] e4. e'4. ~e4 b8) b4. ~b8 r4 b8^.\p b8^. b8^. b8^. b8^. b8^>
~b b16^. b^. b8^. b4.^>~b2.\> ~b8 r4 r4.\!
  \bar "|." 
}

verseSopranoTwoVoicePsalm = \lyricmode {
 % Lyrics follow here.
Glo -- ri -- a, glo -- ri -- a, __ glo -- ri -- a, glo -- ri -- a, glo -- ri -- a Pa -- tri et ﬁ -- li -- o et __ spi -- ri -- tu -- i sanc -- to; __
 sanc -- to, sanc -- to, sanc -- to, __ sanc -- to, sanc -- to, sanc -- to, si -- cut e -- rat in prin -- ci -- pi -- o et nunc et sem -- per __
et __ in sæ -- cu -- la sæ -- cu, sæ -- cu -- la sæ -- cu, sæ -- cu -- la sæ -- cu -- lo -- rum, lo -- rum, lo -- rum, __
a -- men, __ a -- men,  a -- men, a -- men, a -- men, a -- men, a -- men. __
 
}

 sopranoOneVoicePartPsalm = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Sopran 1"
 shortInstrumentName = "S1"
 %midiInstrument = "string ensemble 1"
} << \sopranoOneVoicePsalm 
  %\new Dynamics = Dsone  \with { belowAboveContext = "sopranoOneVoicePart" } \dyn
>>
\addlyrics { \verseSopranoOneVoicePsalm }

sopranoTwoVoicePartPsalm = \new Staff \with {
 #(set-accidental-style 'forget)
 \remove "Time_signature_engraver"
 \override DynamicText.font-size = #-1
 instrumentName = "Sopran 2"
 shortInstrumentName = "S2"
 %midiInstrument = "string ensemble 1"
} { \sopranoTwoVoicePsalm }
\addlyrics { \verseSopranoTwoVoicePsalm }



scoreB = { \new ChoirStaff << \transpose f f {
  <<
    \sopranoOneVoicePartPsalm
    \sopranoTwoVoicePartPsalm

    >>
         } >>
}
\score { \scoreB
  %\include "layout.ily"
  %\midi { \tempo 4 = 92 }
}

\markup {   \override #'(baseline-skip . 2)
 \fill-line {
    \hspace #0.1 % moves the column off the left margin;
        % can be removed if space on the page is tight
     \column {
      \line { 
        \column {
            " "
            "Factus est repente de cœlo sonus" 
            "tamquam advenientis spiritus vehementis"
            "ubi erant sedentes, Alleluja: "
            "et repleti sunt omnes Spiritu Sancto," 
            "loquentes magnalia Dei, Alleluja, Alleluja."
            " "
        }
      }
      }
    \hspace #0.1  % adds horizontal spacing between columns;
        % if they are still too close, add more " " pairs
        % until the result looks good
     \column {
      \line { 
        \column {
            " "
            "Es entstand plötzlich vom Himmel her ein Brausen,"
            "wie von einem daherfahrenden gewaltigen Wind,"
            "wo sie gerade saßen, Alleluja:"
            "Und sie wurden alle vom Heiligen Geist erfüllt"
            "und sprachen von den Großtaten Gottes, Alleluja, Alleluja."
        }
      }
      
    }
  \hspace #0.1 % gives some extra space on the right margin;
      % can be removed if page space is tight
  }
}

\score {
  { \scoreA \scoreB  }
   \midi {  }
}