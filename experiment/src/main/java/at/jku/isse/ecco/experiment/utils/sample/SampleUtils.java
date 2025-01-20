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
        String variantConfigurations = "BASE, DEFINED___LB__IFF_ONE_QUEUE__RB__, DEFINED___LB___WIN32__RB__, DEFINED___LB____clang____RB__, GEN_PATH_TEST, HAVE_UNISTD_H, OCC_H, __OPENVPN_X509_CERT_T_DECLARED; BASE, DEFINED___LB____clang____RB__, HAVE_MBEDTLS_SSL_TLS_PRF, OCC_H, OPENVPN_WIN32_H, static_assert; BASE, DEFINED___LB__IFF_ONE_QUEUE__RB__, DEFINED___LB___WIN32__RB__, DEFINED___LB____clang____RB__, GEN_PATH_TEST, HAVE_UNISTD_H, OCC_H, OPENVPN_WIN32_H, __OPENVPN_X509_CERT_T_DECLARED, static_assert; BASE, DEFINED___LB__IFF_ONE_QUEUE__RB__, DEFINED___LB____clang____RB__, GEN_PATH_TEST, HAVE_MBEDTLS_SSL_TLS_PRF, HAVE_UNISTD_H, OCC_H, OPENVPN_WIN32_H, __OPENVPN_X509_CERT_T_DECLARED, static_assert; BASE, DEFINED___LB___WIN32__RB__, DEFINED___LB____clang____RB__, GEN_PATH_TEST, HAVE_UNISTD_H, OCC_H; BASE, DEFINED___LB__IFF_ONE_QUEUE__RB__, DEFINED___LB___WIN32__RB__, DEFINED___LB____clang____RB__, GEN_PATH_TEST, HAVE_MBEDTLS_SSL_TLS_PRF, OCC_H, OPENVPN_WIN32_H, __OPENVPN_X509_CERT_T_DECLARED, static_assert; BASE, DEFINED___LB__IFF_ONE_QUEUE__RB__, DEFINED___LB___WIN32__RB__, DEFINED___LB____clang____RB__, OCC_H, __OPENVPN_X509_CERT_T_DECLARED, static_assert";
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
