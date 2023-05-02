import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

public class TestClassifier {

    public static void main(String[] args) throws FileNotFoundException, URISyntaxException {
        /* ******************************************************************* */
        /* ************************* STOP WORDS ************************** */
        /* ******************************************************************* */
        // Read in all stop words from the file stop_words.txt
        URL url = TestClassifier.class.getResource("stop_words.txt");
        Path path = Paths.get(url.toURI());
        File file = path.toFile();
        Scanner stopWordsScanner = new Scanner(file);

        ArrayList<String> stopWords = new ArrayList<String>();
        String allWords = stopWordsScanner.nextLine();
        String[] words = allWords.split(" ");
        for (String word : words) {
            stopWords.add(word);
        }

        stopWordsScanner.close();

        /* ******************************************************************* */
        /* ************************* TRAINING DATA ************************** */
        /* ******************************************************************* */

        // Accept two arguments, the file name and the number of entries
        String fileName = args[0];
        String entries = args[1];

        Scanner scanner = new Scanner(new File(fileName));

        ArrayList<String> trainingNames = new ArrayList<String>();
        ArrayList<String> trainingCategories = new ArrayList<String>();
        ArrayList<ArrayList<String>> trainingBiographies = new ArrayList<ArrayList<String>>();

        // The first N entries are the training data
        for (int i = 0; i < Integer.parseInt(entries); i++) {
            String name = scanner.nextLine();
            String category = scanner.nextLine();
            String biography = scanner.nextLine();

            ArrayList<String> normalizedBiography = normalize(biography, stopWords);


            trainingNames.add(name.trim());
            trainingCategories.add(category.trim());
            trainingBiographies.add(normalizedBiography);

            // TODO make more robust
            scanner.nextLine(); // Skip the blank line
        }

        // Compile a count of
        //    (a) For each category C, the number of biographies of category C in the training set T (Occ_T (C))
        //    (b) For each category C and word W in the training set T, the number of biographies of category C that 
        //        contain word W, Occ_T (C) (W|C). It does not matter how many times a word occurs within a given biography, 
        //        as long as it occurs once (this is known as the “Bernoulli method” or, more generally, as a “set of words” model).

        HashMap<String, Integer> categoryBiographiesCount = new HashMap<String, Integer>();
        HashMap<String, HashMap<String, Integer>> categoryWordCounts = new HashMap<String, HashMap<String, Integer>>();

        for (int i = 0; i < trainingBiographies.size(); i++) {
            // For each category C, the number of biographies of category C in the training set T (Occ_T (C))
            String category = trainingCategories.get(i);
            if (categoryBiographiesCount.containsKey(category)) {
                categoryBiographiesCount.put(category, categoryBiographiesCount.get(category) + 1);
            } else {
                categoryBiographiesCount.put(category, 1);
            }

            // For each category C and word W in the training set T, the number of biographies of category C that contain word W, Occ_T (C) (W|C)
            ArrayList<String> biography = trainingBiographies.get(i);
            // Treat "american composer" as two words, "american" and "composer"
            String[] biographyWords = String.join(" ", biography).split(" ");
            // It does not matter how many times a word occurs within a given biography, as long as it occurs once, so we can use a set to get the unique words
            Set<String> uniqueBiographyWords = new HashSet<String>(Arrays.asList(biographyWords));

            for (String word : uniqueBiographyWords) {
                if (categoryWordCounts.containsKey(category)) {
                    HashMap<String, Integer> wordCounts = categoryWordCounts.get(category);
                    if (wordCounts.containsKey(word)) {
                        wordCounts.put(word, wordCounts.get(word) + 1);
                    } else {
                        wordCounts.put(word, 1);
                    }
                } else {
                    HashMap<String, Integer> wordCounts = new HashMap<String, Integer>();
                    wordCounts.put(word, 1);
                    categoryWordCounts.put(category, wordCounts);
                }
            }
        }

        // ***** CORRECT TIL HERE!!! *****

        // Probabilities
        // (a) For each classification C, define FreqT (C) = Occ_T (C)/|T|, the fraction of the biographies that are of category C. For instance, in 
        //     the tiny training corpus, FreqT (Government) = 2/5.
        //     
        //     For each classification C and word W, define FreqT (W|C) = Occ_T (W|C)/Occ_T (C), the fraction of biographies of category C 
        //     that contain W. For instance, in the tiny training corpus, FreqT (”american”|Government) = 1/2, since there are two biographies of category Government
        //     and one of these has the word “american”.

        HashMap<String, Double> categoryFrequencies = new HashMap<String, Double>();
        HashMap<String, HashMap<String, Double>> wordFrequencies = new HashMap<String, HashMap<String, Double>>();

        for (String category : categoryBiographiesCount.keySet()) {
            categoryFrequencies.put(category, (double) categoryBiographiesCount.get(category) / trainingBiographies.size());
        }

        for (String category : categoryWordCounts.keySet()) {
            HashMap<String, Integer> wordCounts = categoryWordCounts.get(category);
            HashMap<String, Double> wordFrequenciesForCategory = new HashMap<String, Double>();
            for (String word : wordCounts.keySet()) {
                wordFrequenciesForCategory.put(word, (double) wordCounts.get(word) / categoryBiographiesCount.get(category));
            }
            wordFrequencies.put(category, wordFrequenciesForCategory);
        }

        // ***** LOOKS GOOD TIL HERE!!! *****

        // Probabilities
        // (b) For each classification C and word W, compute the probabilities using the Laplacian correction. Let epsilon = 0.1.
        //     P(C) = (FreqT (C) + epsilon) / (1 + |C| * epsilon) [Note: FreqT (C) is categoryFrequencies.get(C)]
        //     P(W|C) = (FreqT (W|C) + epsilon) / (1 + 2 * epsilon) [Note: FreqT (W|C) is wordFrequencies.get(C).get(W)]

        double epsilon = 0.1;
        HashMap<String, Double> categoryProbabilities = new HashMap<String, Double>();
        HashMap<String, HashMap<String, Double>> wordProbabilities = new HashMap<String, HashMap<String, Double>>();

        for (String category : categoryFrequencies.keySet()) {
            categoryProbabilities.put(category, (categoryFrequencies.get(category) + epsilon) / (1 + categoryFrequencies.size() * epsilon));
        }

         /* Testing */
         TreeSet<String> allWordsToCheck = new TreeSet<String>();
         for (ArrayList<String> biographies : trainingBiographies) {
             String[] biographyWords = String.join(" ", biographies).split(" ");
             allWordsToCheck.addAll(Arrays.asList(biographyWords));
         }


         for (String category: wordFrequencies.keySet()) {
            HashMap<String, Double> wordProbabilitiesForCategory = new HashMap<String, Double>();
            for (String word : allWordsToCheck) {
                if (wordFrequencies.get(category).containsKey(word)) {
                    wordProbabilitiesForCategory.put(word, (wordFrequencies.get(category).get(word) + epsilon) / (1 + 2 * epsilon));
                } else {
                    wordProbabilitiesForCategory.put(word, epsilon / (1 + 2 * epsilon));
                }
            }
            wordProbabilities.put(category, wordProbabilitiesForCategory);
         } 

         /* This was not accounting for all words, just the words that were associated with that category */
        // for (String category : wordFrequencies.keySet()) {
        //     HashMap<String, Double> wordProbabilitiesForCategory = new HashMap<String, Double>();
        //     for (String word : wordFrequencies.get(category).keySet()) {
        //         wordProbabilitiesForCategory.put(word, (wordFrequencies.get(category).get(word) + epsilon) / (1 + 2 * epsilon));
        //     }
        //     wordProbabilities.put(category, wordProbabilitiesForCategory);
        // }

        // Probabilities
        // (c) Compute negative log probabilities to avoid underflow (use base 2 for the logarithm). 
        //     For each classification C define L(C) = − log2(P(C)) and define L(W|C) = − log2(P(W|C)). 
        //     For instance, L(Government) = 1.3785 and L(”american”|Government) = 1.0.
        //     [Note: In Java, Math.log computes the natural logarithm. To compute the base 2 logarithm, use Math.log(x)/Math.log(2).]

        HashMap<String, Double> categoryLogProbabilities = new HashMap<String, Double>();
        HashMap<String, HashMap<String, Double>> wordLogProbabilities = new HashMap<String, HashMap<String, Double>>();

        for (String category : categoryProbabilities.keySet()) {
            categoryLogProbabilities.put(category, -Math.log(categoryProbabilities.get(category)) / Math.log(2));
        }

        for (String category : wordProbabilities.keySet()) {
            HashMap<String, Double> wordLogProbabilitiesForCategory = new HashMap<String, Double>();
            for (String word : wordProbabilities.get(category).keySet()) {
                wordLogProbabilitiesForCategory.put(word, -Math.log(wordProbabilities.get(category).get(word)) / Math.log(2));
            }
            wordLogProbabilities.put(category, wordLogProbabilitiesForCategory);
        }


        // Store a Set of all words that appear in the training data (this can be found by checking the wordLogProbabilities)
        HashSet<String> allWordsInTrainingData = new HashSet<String>();
        for (String category : wordLogProbabilities.keySet()) {
            for (String word : wordLogProbabilities.get(category).keySet()) {
                allWordsInTrainingData.add(word);
            }
        }

        /* ******************************************************************* */
        /* *************************** TEST DATA ***************************** */
        /* ******************************************************************* */

        ArrayList<String> testNames = new ArrayList<String>();
        ArrayList<String> testCategories = new ArrayList<String>();
        ArrayList<ArrayList<String>> testBiographies = new ArrayList<ArrayList<String>>();

        // Since the training date went from entry 0 to N, we can just parse from N + 1 to the end
        int index = 0;
        while (scanner.hasNextLine()) {
            String name = scanner.nextLine();
            String category = scanner.nextLine().trim();
            String biography = scanner.nextLine();

            ArrayList<String> normalizedBiography = normalize(biography, stopWords);

            // On top of the initial normalization, we want to skip any word that did not appear at all in the training data
            // (1) Treat each word as it's own word: "british politician, socialist" should be trated as three words
            // (2) If a word does not appear in the training data, remove it from the biography
            
            String[] listOfAllNormalizedBiographyWords = String.join(" ", normalizedBiography).split(" ");

            ArrayList<String> normalizedBiographyWithoutWordsNotInTrainingData = new ArrayList<String>();
            ArrayList<String> wordsRemovedFromBiographySinceNotInTrainingData = new ArrayList<String>();
            for (String word : listOfAllNormalizedBiographyWords) {
                if (allWordsInTrainingData.contains(word)) {
                    normalizedBiographyWithoutWordsNotInTrainingData.add(word);
                } else {
                    wordsRemovedFromBiographySinceNotInTrainingData.add(word);
                }
            }

            testNames.add(name);
            testCategories.add(category);
            testBiographies.add(normalizedBiographyWithoutWordsNotInTrainingData);

            index++;

            // TODO make more robust
            scanner.nextLine(); // Skip the empty line
        }

        // For each category C, compute L(C|Biography) = L(C) + \sum_(W \in Biography) L(W|C). As in the learning phase,
        //    (a) Each word counts only once, no matter how often it appears in the biography
        //    (b) DO NOT USE THE CATEGORY ATTACHED TO THE BIOGRAPHY
        //    [Note: L(C) is categoryLogProbabilities.get(C) and L(W|C) is wordLogProbabilities.get(C).get(W).]

        LinkedHashMap<String, String> predictions = new LinkedHashMap<String, String>();
        LinkedHashMap<String, LinkedHashMap<String, Double>> actualProbabilities = new LinkedHashMap<String, LinkedHashMap<String, Double>>();

        for (int i = 0; i < testNames.size(); i++) {
            String name = testNames.get(i);
            ArrayList<String> biography = testBiographies.get(i);
            HashMap<String, Double> biographyLogProbabilities = new HashMap<String, Double>();

            for (String category : testCategories) {

                if (biographyLogProbabilities.containsKey(category)) {
                    continue;
                }

                // L(C)
                double logProbabilityForCategory = categoryLogProbabilities.get(category);

                // Compute L(W|C) for each word in the biography
                for (String word : biography) {
                    if (wordLogProbabilities.containsKey(category) && wordLogProbabilities.get(category).containsKey(word)) {
                        double logProbabilityForWordGivenCategory = wordLogProbabilities.get(category).get(word);
                        logProbabilityForCategory += logProbabilityForWordGivenCategory;
                    }
                }

                biographyLogProbabilities.put(category, logProbabilityForCategory);
            }

            // The prediction of the algorithm is the category C with the smallest value of L(C|B). 
            // [Note: L(C|B) is biographyLogProbabilities.get(C).get(B).]
            String predictedCategory = "";
            double minLogProbability = Double.MAX_VALUE;
            for (String category : biographyLogProbabilities.keySet()) {
                if (biographyLogProbabilities.get(category) < minLogProbability) {
                    predictedCategory = category;
                    minLogProbability = biographyLogProbabilities.get(category);
                }
            }

            predictions.put(name, predictedCategory);

            // To recover the actual probabilities: Let k be the number of categories.
            //    (a) For i = 1...k, let ci = L(Ci | B), the values of L for all the different categories. Let m = min_i c_i, the smallest value of these
            //    (b) For i = 1...k, if c_i - m < 6, then x_i = 2^(m-c_i) else x_i = 0.
            //    (c) Let s = \sum_i x_i, for i = 1...k, P(C_k | B) = x_i / s
            // Sample actual probabilities: Government: 0.44 Music: 0.07 Writer: 0.48
            LinkedHashMap<String, Double> actualProbability = new LinkedHashMap<String, Double>();
            
            // (a)
            double minLogProbabilityForBiography = Double.MAX_VALUE;
            for (String category : biographyLogProbabilities.keySet()) {
                if (biographyLogProbabilities.get(category) < minLogProbabilityForBiography) {
                    minLogProbabilityForBiography = biographyLogProbabilities.get(category);
                }
            }

            // (b)
            double sumOfX = 0;
            for (String category : biographyLogProbabilities.keySet()) {
                double logProbabilityForCategory = biographyLogProbabilities.get(category);
                double x = 0;
                if (logProbabilityForCategory - minLogProbabilityForBiography < 6) {
                    x = Math.pow(2, minLogProbabilityForBiography - logProbabilityForCategory);
                }
                actualProbability.put(category, x);
                sumOfX += x;
            }

            // (c)
            for (String category : actualProbability.keySet()) {
                double x = actualProbability.get(category);
                actualProbability.put(category, Math.round((x / sumOfX) * 100.0) / 100.0);
            }

            actualProbabilities.put(name, actualProbability);
        }


        // Output
        String format = "%-30s %-30s %-30s%n";
        for (String name : predictions.keySet()) {
            boolean isCorrectPrediction = predictions.get(name).equals(testCategories.get(testNames.indexOf(name)));
            String predictionText = "Prediction: " + predictions.get(name);
            String correctPredictionText = (isCorrectPrediction) ? "Right." : "Wrong.";
            System.out.printf(format, name + ".", predictionText + ".", correctPredictionText);
            for (String category : actualProbabilities.get(name).keySet()) {
                System.out.print(category + ": " + actualProbabilities.get(name).get(category));
                System.out.print("\t\t");
            }
            System.out.println();
            System.out.println();
        }

        int numberCorrect = 0;
        for (String name : predictions.keySet()) {
            if (predictions.get(name).equals(testCategories.get(testNames.indexOf(name)))) {
                numberCorrect++;
            }
        }
        double accuracy = (double) numberCorrect / (double) predictions.size();
        System.out.println("Overall accuracy: " + numberCorrect + " out of " + predictions.size() + " = " + accuracy);

    }

    public static ArrayList<String> normalize(String biography, ArrayList<String> stopWords) {
        // Get rid of extra spaces between words, i.e. "the  fox" -> "the fox"
        biography = biography.replaceAll(" +", " ");

        // Normalization: All words in the biography should be normalized to lowercase
        biography = biography.toLowerCase();

        // Replaces all instances of " and " but not ", and " with a comma
        // so that "politician, musician, and composer" becomes "politician, musician, composer"
        // but also so that "politician and musician" becomes "politician, musician"
        biography = biography.replaceAll(" and ", ", ");
        
        // Replace all instances of a double comma with a single comma
        biography = biography.replaceAll(",,", ",");

        // Replace all . with nothing
        biography = biography.replaceAll("\\.", "");

        // Stop words should be omitted. Stop words are: 
        //    (a) any word of one or two letters; 
        //    (b) any word in the list of stopwords provided
        String[] biographyWords = biography.split(" ");
        String newBiography = "";
        ArrayList<String> removedWords = new ArrayList<String>();
        for (String word : biographyWords) {
            if (word.length() > 2 && !stopWords.contains(word)) {
                newBiography += word + " ";
            } else {
                removedWords.add(word);
            }
        }
        newBiography = newBiography.trim();

        // The biography is a list (i.e. composer, politcian, German singer, etc.) so split it into a list
        ArrayList<String> splitBiography = new ArrayList<String>();
        String[] newBiographyWords = newBiography.split(",");
        for (String word : newBiographyWords) {
            splitBiography.add(word.trim());
        }

        return splitBiography;
    }
}
