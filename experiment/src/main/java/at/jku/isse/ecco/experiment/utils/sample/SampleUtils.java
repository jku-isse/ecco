package at.jku.isse.ecco.experiment.utils.sample;

import at.jku.isse.ecco.experiment.sample.VevosFeatureSampler;
import org.variantsync.vevos.simulation.io.Resources;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class SampleUtils {

    public static void main(String[] args) throws Resources.ResourceIOException, IOException {
        Path vevosGroundTruthBasePath = Paths.get("C:\\Users\\Berni\\Desktop\\Project\\Tools\\VEVOS_Extraction\\ground-truth");
        String repository = "openvpn";
        Path sampleBasePath = Paths.get("C:\\Users\\Berni\\Desktop\\Project\\ecco\\experiment\\src\\integrationTest\\resources\\strange_sample");
        String variantConfigurations = "BASE, COMMON_H, DEFINED___LB__HAVE_DUP__RB__, HAVE_CONFIG_H, PLATFORM_H, VALIDATE_H, _ARM64_, _Inout_opt_, _WIN64; BASE, COMMON_H, DEFINED___LB__HAVE_DUP__RB__, HAVE_CONFIG_H, PLATFORM_H, SSL_UTIL_H_, _ARM64_, _Inout_opt_, __OPENVPN_X509_CERT_T_DECLARED; BASE, COMMON_H, DEFINED___LB__HAVE_DUP__RB__, PLATFORM_H, SSL_UTIL_H_, VALIDATE_H, _ARM64_, _Inout_opt_, _WIN64, __OPENVPN_X509_CERT_T_DECLARED";
        createSampleVariantsWithoutBaseFeature(vevosGroundTruthBasePath, repository, sampleBasePath, variantConfigurations);
    }

    /**
     *
     * @param variantConfigurations semicolon-separated variant configurations, which are
     *                                                comma-separated feature-names.
     */
    public static void createSampleVariantsWithoutBaseFeature(Path vevosGroundTruthBasePath, String repository, Path sampleBasePath, String variantConfigurations) throws Resources.ResourceIOException, IOException {
        String[] variantConfigurationsArray = variantConfigurations.split(";");
        List<String> variantConfigurationList = Arrays.stream(variantConfigurationsArray).map(String::trim).toList();
        List<List<String>> featureLists = variantConfigurationList.stream().map(variantConfiguration -> {
            String[] featureArray = variantConfiguration.split(",");
            List<String> featureNames = new java.util.ArrayList<>(Arrays.stream(featureArray).map(String::trim).toList());
            featureNames.remove("BASE");
            return featureNames;
        }).toList();

        VevosFeatureSampler sampler = new VevosFeatureSampler();
        sampler.createSampleVariants(vevosGroundTruthBasePath, repository, sampleBasePath, featureLists);
    }


}
