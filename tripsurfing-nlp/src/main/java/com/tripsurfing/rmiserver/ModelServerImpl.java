package com.tripsurfing.rmiserver;


import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.crf.CRFCliqueTree;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import tk.lsh.Common;
import tk.lsh.LSHTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.*;

public class ModelServerImpl implements ModelServer {

    private static final Logger logger = LoggerFactory.getLogger(ModelServerImpl.class);

    private Properties properties;
    private Set<String> dictionary;
    private MaxentTagger tagger;
    private int LIMIT_LENGTH = 6;
    private CRFClassifier<CoreLabel> classifier;
    private LSHTable lsh;

    public ModelServerImpl() {
        try {
            properties = new Properties();
            properties.load(this.getClass().getClassLoader().getResourceAsStream("vivut.properties"));
        } catch (Exception e) {
            //
        }
    }

    public ModelServerImpl(String configFile) {
        try {
            properties = new Properties();
            properties.load(new FileInputStream(configFile));
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    
    private void loadDictionary() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(this.getClass().
                getClassLoader().getResourceAsStream("tripsurfing.dict")));

        dictionary = new HashSet<String>();
        lsh = new LSHTable(2, 8, 100, 999999999, 0.5);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0)
                    continue;
                String placeName = line.toLowerCase();
                dictionary.add(placeName);
                lsh.put(Common.getCounterAtTokenLevel(placeName));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("Loaded: {} names.", dictionary.size());
    }

    public List<int[]> getNames(String text) {
        if (classifier == null) {
            classifier = CRFClassifier.getClassifierNoExceptions(properties.getProperty("NER_TAGGER"));
        }
        List<int[]> names = new ArrayList<int[]>();
        List<List<CoreLabel>> out = classifier.classify(text);
        int tokenIndex = 0;
        for (List<CoreLabel> cl : out) {
            CRFCliqueTree<String> cliqueTree = classifier.getCliqueTree(cl);
            int i = 0;
            while (i < cliqueTree.length()) {
                CoreLabel word = cl.get(i);
                if (word.get(AnswerAnnotation.class).equalsIgnoreCase("O")) {
                    i++;
                    continue;
                }
                int j = i;
                while (j < cl.size()
                        && cl.get(j).get(AnswerAnnotation.class).equalsIgnoreCase(word.get(AnswerAnnotation.class)))
                    j++;
                names.add(new int[]{i + tokenIndex, j + tokenIndex}); // from i-th to (j-1)th
                i = j;
            }
            tokenIndex += cliqueTree.length();
        }
        return names;
    }


    public List<String> recognizeMentions(String sentence) throws RemoteException {
        if (dictionary == null) {
            loadDictionary();
        }
        if (tagger == null) {
            // Initialize the tagger
            tagger = new MaxentTagger(properties.getProperty("POS_TAGGER"));
        }
        // The tagged string
        String tagged = tagger.tagString(sentence);
        // TODO: implement tokenizer or call Stanford tokenizer
        List<String> res = new ArrayList<String>();
        String[] tokens = tagged.split(" ");
        List<int[]> names = getNames(sentence);
        boolean[] isNames = new boolean[tokens.length];
        int nameIndex = 0;
        for (int i = 0; i < tokens.length; ) {
            if (nameIndex < names.size() && names.get(nameIndex)[1] == i) {
                boolean added = true, overlap = false;
                for (int t = names.get(nameIndex)[0]; t < names.get(nameIndex)[1]; t++) {
                    if (!isNames[t])
                        added = false;
                    else
                        overlap = true;
                }
                if (!added) {
                    if (overlap)
                        res.remove(res.size() - 1);
                    String s = "";
                    for (int t = names.get(nameIndex)[0]; t < names.get(nameIndex)[1]; t++)
                        s += " " + tokens[t].split("_")[0];
                    String canName = s.substring(1);
                    System.out.println("canName: " + canName); 
//                    if (dictionary.contains(canName.toLowerCase()))
//                    	res.add(canName);
                    if(lsh.deduplicate(Common.getCounterAtTokenLevel(canName.toLowerCase())).size() > 0)
                    	res.add(canName);
                }
            }
            while (nameIndex < names.size() &&
                    names.get(nameIndex)[1] < i) {
                nameIndex++;
            }
            int nextPos = i + 1;
            String[] info = tokens[i].split("_");
            if (info.length > 1 && info[1].equalsIgnoreCase("NNP")) {
                for (int j = LIMIT_LENGTH; j >= 0; j--) {
                    String s = info[0];
                    for (int t = 1; t < j && i + t < tokens.length; t++)
                        s += " " + tokens[i + t].split("_")[0];
                    if (dictionary.contains(s.toLowerCase())) {
                        res.add(s);
                        nextPos = i + j;
                        for (int t = 0; t < j && i + t < tokens.length; t++)
                            isNames[i + t] = true;
                        break;
                    }
                }
            }
            i = nextPos;
        }
        return res;
    }
}
