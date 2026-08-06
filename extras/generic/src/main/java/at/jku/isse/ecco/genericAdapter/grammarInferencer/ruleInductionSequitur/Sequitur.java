package at.jku.isse.ecco.genericAdapter.grammarInferencer.ruleInductionSequitur;
/*
This class is part of a Java port of Craig Nevill-Manning's Sequitur algorithm.
Copyright (C) 1997 Eibe Frank

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import java.util.List;

public class Sequitur {

/*
    public String runAlgorithm(String inputText, boolean printInATLRFormat) {

        Rule firstRule = new Rule();

        Tokenizer tokenizer;
        try {
            tokenizer = new Tokenizer(inputText);

            Rule.numRules = 0;
            Symbol.theDigrams.clear();
            String curSymbol = tokenizer.nextToken();
            while (curSymbol != null) {
                firstRule.last().insertAfter(new Terminal(curSymbol));
                firstRule.last().p.check();
                curSymbol = tokenizer.nextToken();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return printInATLRFormat ? firstRule.getAntlrRules() : firstRule.getRules();
    }
    */

    public at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Symbol runAlgorithm(List<String> sample) {
        Rule firstRule = new Rule();
            Rule.numRules = 0;
            Symbol.theDigrams.clear();

        sample.stream().filter(curSymbol -> curSymbol != null).forEach(curSymbol -> {
            firstRule.last().insertAfter(new Terminal(curSymbol));
            firstRule.last().p.check();
        });

        return SequiturRuleConverter.convertToInternalDataStructure(firstRule);
    }

}
