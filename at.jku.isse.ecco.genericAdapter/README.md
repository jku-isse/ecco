
# ECCO Generic Adapter
by Michael Jahn

## Grammar Inference

The Grammar Inference module can be used in any context, where generating a grammar from examples is applicable. Follow these steps to infer a grammar for a file type by positive examples only;

<ol>
     <li>Implement EccoModelBuilderStrategy for the new file type.</li>
    <li>Register the new strategy implementation in the GrammarInferencerMain class.</li>
    <li>Run GrammarInferencerMain with arguments
    "-output [outputFile] -input [list of input file paths]"
        </li>
    <li>The resulting grammar will be written to the specified output path in ANTLR syntax</li>
</ol>

The process can also be started during runtime, by using the _inferGrammar_ method from the [GrammarInferenceFacade](src/main/java/at/jku/isse/ecco/genericAdapter/grammarInferencer/facade/GrammarInferenceFacade.java) and passing the implemented strategy, along with the input files and output directory, as parameters.

Additionally to inferring a grammar from input files, an existing grammar can also be updated. To do so, use the method _updateGrammar_ from [GrammarInferenceFacade](at.jku.isse.ecco.genericAdapter/src/main/java/at/jku/isse/ecco/genericAdapter/grammarInferencer/facade/GrammarInferenceFacade.java).
The grammarDataFile (*.grDat), which is a mandatory parameter, is generated in the same directory as the grammar file when generating the initial grammar (either using the command line tool, or the facade call).
Basically, better results will be achieved when all input files are present in the beginning and the initial infer grammar method is used, but the update grammar method provides a way to
adapt an existing grammar to a new file without the need ot having all original files available.

For **hints** on how to implement the strategy for a new type, refer to the javadoc of [EccoModelBuilderStrategy](at.jku.isse.ecco.genericAdapter/src/main/java/at/jku/isse/ecco/genericAdapter/eccoModelAdapter/strategy/EccoModelBuilderStrategy.java) or the example implementation for
the STEP and Java file format, available in [StpEccoModelBuilderStrategy](at.jku.isse.ecco.genericAdapter/src/main/java/at/jku/isse/ecco/genericAdapter/eccoModelAdapter/strategy/StpEccoModelBuilderStrategy.java) and [JavaEccoModelBuilderStrategy](at.jku.isse.ecco.genericAdapter/src/main/java/at/jku/isse/ecco/genericAdapter/eccoModelAdapter/strategy/JavaEccoModelBuilderStrategy.java).

To generate a parser from the grammar either the [AntlrParserWrapperServiceImpl](at.jku.isse.ecco.genericAdapter/src/main/java/at/jku/isse/ecco/genericAdapter/parserGenerator/AntlrParserWrapperServiceImpl) module can be used, or the [ANTLR](http://www.antlr.org/) tool chain directly, in version 4.
But be aware, that the input string to the parser needs to be already tokenized, using the Token Definitions from the type strategy! (This can be done, by running the _tokenizeToString_ method of [Tokenizer](at.jku.isse.ecco.genericAdapter/src/main/java/at/jku/isse/ecco/genericAdapter/grammarInferencer/tokenization/Tokenizer.java) and pass the resulting string to the generated parser.
<br>
<br>
## ECCO Adapter

The ECCO Adapter makes use of the Grammar Inference module and can be used as any other ECCO Adapter, by adding [GenericAdapterReader](/src/main/java/at/jku/isse/ecco/genericAdapter/eccoModelAdapter/builder/GenericAdapterReader.java) and [GenericAdapterWriter](/src/main/java/at/jku/isse/ecco/genericAdapter/eccoModelAdapter/builder/GenericAdapterWriter.java) to the dependencies. New type strategy imlementations need to be registered in both classes.