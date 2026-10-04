package at.jku.isse.ecco.adapter.lilypond.test.thesis;

import java.util.HashSet;

public class DieuFeatureCode {

    private final static ConfigurationCodeBuilder ccb;

    static {
        ccb = new ConfigurationCodeBuilder();
        //************* header
        ccb.add("""
                        \\version "2.22.0"
                        \\language "deutsch"
                        \\paper { tagline ="" }

                        #(set-default-paper-size "a4landscape")

                        \\header {
                          title = "Dieu! qu'il la fait bon regarder!"
                          poet = "Charles d'Orl\u00E9ans"
                          composer = "Claude Debussy"
                          tagline = ""
                        }

                        global = {
                          \\key h \\major
                          \\time 3/4
                          \\autoBeamOff
                          \\dynamicUp
                          \\phrasingSlurUp
                        }
                        
                        """, "header.1")
        //************* Sopran
                .add("""
                        SopranoVoice = \\relative c' {
                          \\global
                          \\clef "treble"
                          \\tempo "Tr\u00E8s mod\u00E9r\u00E9 soutenu et expressif" 4 = 96
                          r4 fis2""", "sopNotes.1")
                .add("^-", "sopArticulation.1")
                .add("\\mf\\>", "sopDynamics.1")
                .add("\\(", "sopSlurs.1")
                .add(" gis8", "sopNotes.1")
                .add("\\p", "sopDynamics.1")
                .add(" gis8 fis8 fis8 gis8 gis8\n" +
                        "  fis2", "sopNotes.1")
                .add("\\)", "sopSlurs.1")
                .add(" fis4", "sopNotes.1")
                .add("\\<", "sopDynamics.1")
                .add("\\(", "sopSlurs.1")
                .add(" gis8 gis8 cis8 cis8 ais8", "sopNotes.1")
                .add("\\>", "sopDynamics.1")
                .add(" fis8", "sopNotes.1")
                .add("\\!", "sopDynamics.1")
                .add("\n" +
                        "  fis4 fis4", "sopNotes.1")
                .add("\\)", "sopSlurs.1")
                .add("""
                         r4 \\bar "|."
                        }

                        """, "sopNotes.1")
                .add("""
                        verseSopranoVoice = \\lyricmode {
                          Dieu! qu'il la fait bon re -- gar -- der
                          La gra -- ci -- eu -- se bonne et bel -- le;
                        }

                        """, "sopLyrics.1")
        //************* Alt
                .add("""
                        AltoVoice =  \\relative c' {
                          \\global
                          \\clef "treble"
                          \\tempo "Tr\u00E8s mod\u00E9r\u00E9 soutenu et expressif" 4 = 96
                          r4 cis2""", "altNotes.1")
                .add("^-", "altArticulation.1")
                .add("\\mf\\>", "altDynamics.1")
                .add("\\(", "altSlurs.1")
                .add("\n" +
                        "  e8", "altNotes.1")
                .add("\\p", "altDynamics.1")
                .add(" \\times 2/3 { dis16", "altNotes.1")
                .add("[", "altBeams.1")
                .add(" e16 dis16", "altNotes.1")
                .add("]", "altBeams.1")
                .add(" } cis8 fis8 e8\n" +
                        "  \\times 2/3 { dis16", "altNotes.1")
                .add("[", "altBeams.1")
                .add(" e16 dis16", "altNotes.1")
                .add("]", "altBeams.1")
                .add(" }\n" +
                        "  cis2", "altNotes.1")
                .add("\\)", "altSlurs.1")
                .add(" cis4", "altNotes.1")
                .add("\\<", "altDynamics.1")
                .add("\\(", "altSlurs.1")
                .add(" e8 e16", "altNotes.1")
                .add("[", "altBeams.1")
                .add(" fis16", "altNotes.1")
                .add("]", "altBeams.1")
                .add(" gis8\n" +
                        "  \\times 2/3  { fis16", "altNotes.1")
                .add("[", "altBeams.1")
                .add(" gis16 fis16", "altNotes.1")
                .add("]", "altBeams.1")
                .add(" }\n" +
                        "  e8", "altNotes.1")
                .add("\\>", "altDynamics.1")
                .add(" dis8", "altNotes.1")
                .add("\\!", "altDynamics.1")
                .add(" cis4 cis4", "altNotes.1")
                .add("\\)", "altSlurs.1")
                .add("""
                         r4
                        }

                        """, "altNotes.1")
                .add("""
                        verseAltoVoice = \\lyricmode {
                          Dieu! qu'il la __ fait bon re -- gar -- der
                          La gra -- ci -- eu -- se __ bonne et bel -- le;
                        }

                        """, "altLyrics.1")
                //************* Tenor
                .add("""
                        TenorVoice = \\relative c {
                          \\global
                          \\clef "treble_8"
                          \\tempo "Tr\u00E8s mod\u00E9r\u00E9 soutenu et expressif" 4 = 96
                          r4 ais'2""", "tenNotes.1")
                .add("^-", "tenArticulation.1")
                .add("\\mf\\>", "tenDynamics.1")
                .add("\\(", "tenSlurs.1")
                .add("\n" +
                        "  cis8", "tenNotes.1")
                .add("\\p", "tenDynamics.1")
                .add(" h8 ais8\n" +
                        "  \\times 2/3 { ais16", "tenNotes.1")
                .add("[", "tenBeams.1")
                .add(" h16 ais16", "tenNotes.1")
                .add("]", "tenBeams.1")
                .add(" } cis8\n" +
                        "  \\times 2/3 { h16", "tenNotes.1")
                .add("[", "tenBeams.1")
                .add(" cis16 h16", "tenNotes.1")
                .add("]", "tenBeams.1")
                .add(" }\n" +
                        "  ais2", "tenNotes.1")
                .add("\\)", "tenSlurs.1")
                .add(" ais4", "tenNotes.1")
                .add("\\<", "tenDynamics.1")
                .add("\\(", "tenSlurs.1")
                .add(" h8 \\times 2/3  { h16", "tenNotes.1")
                .add("[", "tenBeams.1")
                .add(" cis16 dis16", "tenNotes.1")
                .add("]", "tenBeams.1")
                .add(" }\n" +
                        "  e8 \\times 2/3  { dis16", "tenNotes.1")
                .add("[", "tenBeams.1")
                .add(" e16 dis16", "tenNotes.1")
                .add("]", "tenBeams.1")
                .add(" } cis8.", "tenNotes.1")
                .add("\\>", "tenDynamics.1")
                .add(" h16", "tenNotes.1")
                .add("\\!", "tenDynamics.1")
                .add("\n" +
                        "  h8", "tenNotes.1")
                .add("[", "tenBeams.1")
                .add(" ais16 gis16", "tenNotes.1")
                .add("]", "tenBeams.1")
                .add(" fis4", "tenNotes.1")
                .add("\\)", "tenSlurs.1")
                .add("""
                         r4
                        }

                        """, "tenNotes.1")
                .add("""
                        verseTenorVoice = \\lyricmode {
                          Dieu! qu'il la fait bon __ re -- gar -- der
                          La gra -- ci -- eu -- se __ bonne et bel -- le;
                        }

                        """, "tenLyrics.1")
                //************* Bass
                .add("""
                        BassVoice =  \\relative c {
                          \\global
                          \\clef "bass"
                          \\tempo "Tr\u00E8s mod\u00E9r\u00E9 soutenu et expressif" 4 = 96
                          r4 fis2""", "basNotes.1")
                .add("^-", "basArticulation.1")
                .add("\\mf\\>", "basDynamics.1")
                .add("\\(", "basSlurs.1")
                .add("\n" +
                        "  cis8", "basNotes.1")
                .add("\\p", "basDynamics.1")
                .add(" cis8 fis8 fis8 cis8 dis16", "basNotes.1")
                .add("[", "basBeams.1")
                .add(" e16", "basNotes.1")
                .add("]", "basBeams.1")
                .add("\n" +
                        "  fis2", "basNotes.1")
                .add("\\)", "basSlurs.1")
                .add(" fis4", "basNotes.1")
                .add("\\<", "basDynamics.1")
                .add("\\(", "basSlurs.1")
                .add(" e8 dis8 cis4", "basNotes.1")
                .add("~", "basSlurs.1")
                .add(" cis16", "basNotes.1")
                .add("\\>", "basDynamics.1")
                .add(" cis16 dis16 e16", "basNotes.1")
                .add("\\!", "basDynamics.1")
                .add("\n" +
                        "  fis4", "basNotes.1")
                .add("~", "basSlurs.1")
                .add(" fis8", "basNotes.1")
                .add("[", "basBeams.1")
                .add(" e16 dis16", "basNotes.1")
                .add("]", "basBeams.1")
                .add(" cis8 fis8", "basNotes.1")
                .add("\\)", "basSlurs.1")
                .add("""

                        }

                        """, "basNotes.1")
                .add("""
                        verseBassVoice = \\lyricmode {
                          Dieu! qu'il la fait bon re -- gar -- der
                          La gra -- ci -- eu -- se bonne et bel -- _ le;
                        }

                        """, "basLyrics.1")
                //************* Score
                .add("""
                        \\score {
                          <<
                            \\new ChoirStaff <<
                          
                        """, "header.1")
                .add("""
                              \\new Staff = "Sopran" <<
                                \\set Staff.instrumentName = "Sopran"
                                \\set Staff.shortInstrumentName = "S"
                                \\new Voice = "SopranoVoice" { \\SopranoVoice }
                        """, "sopNotes.1")
                .add("        \\new Lyrics \\lyricsto \"SopranoVoice\" \\verseSopranoVoice\n", "sopLyrics.1")
                .add("""
                              >>

                        """, "sopNotes.1")
                .add("""
                              \\new Staff = "Alt" <<
                                \\set Staff.instrumentName = "Alt"
                                \\set Staff.shortInstrumentName = "A"
                                \\new Voice = "AltoVoice" { \\AltoVoice }
                        """, "altNotes.1")
                .add("        \\new Lyrics \\lyricsto \"AltoVoice\" \\verseAltoVoice\n", "altLyrics.1")
                .add("""
                              >>

                        """, "altNotes.1")
                .add("""
                              \\new Staff = "Tenor" <<
                                \\set Staff.instrumentName = "Tenor"
                                \\set Staff.shortInstrumentName = "T"
                                \\new Voice = "TenorVoice" { \\TenorVoice }
                        """, "tenNotes.1")
                .add("        \\new Lyrics \\lyricsto \"TenorVoice\" \\verseTenorVoice\n", "tenLyrics.1")
                .add("""
                              >>

                        """, "tenNotes.1")
                .add("""
                              \\new Staff  = "Bass" <<
                                \\set Staff.instrumentName = "Bass"
                                \\set Staff.shortInstrumentName = "B"
                                \\new Voice = "BassVoice" { \\BassVoice }
                        """, "basNotes.1")
                .add("        \\new Lyrics \\lyricsto \"BassVoice\" \\verseBassVoice\n", "basLyrics.1")
                .add("""
                              >>
                             \s
                        """, "basNotes.1")
                .add("""
                            >>
                          >>
                          \\layout{}
                        }
                        """, "header.1");
    }

    public static String getCode(HashSet<String> configuration) {
        return ccb.getCodeForConfiguration(configuration);
    }
}
