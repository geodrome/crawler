import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import java.util.*;

public class Crawler {
    public static void main(String[] args) {
        String url = "http://jsoup.org";
        Crawler crawler = new Crawler();
        String html = crawler.getHtml(url);

        String[] words = crawler.getWords(html);
        //for (String w : words)
        //   System.out.println(w);

        String[] links = crawler.getLinks(html, url);
        //for (String l : links)
        //    System.out.println(l);

        crawler.index(url, 1);
        //crawler.printUrlIndex();
        //crawler.printWordIndex();
        System.out.println("*** Search results *** ");
        TreeMap<String, Double> results = crawler.search("jsoup");
        for (Map.Entry<String, Double> entry : results.entrySet())
            System.out.println(entry.getKey() + " " + entry.getValue());
    }

    public void index(String url, int depth) {
        String html = getHtml(url);
        if (! urlIndex.containsKey(url)) {
            indexOne(url, html);
        }
        if (depth > 0) {
            String[] links = getLinks(html, url);
            for (String link: links) {
                // how to do this in parallel?
                index(link, depth - 1);
            }
        }
    }

    public TreeMap<String, Double> search(String query) {
        //System.out.println("Searching... "+query);
        String[] terms = query.toLowerCase().split("\\W+");
        //System.out.println("terms: "+terms);
        Set<String> docs = getUnion(terms);
        //System.out.println("docs: "+docs);
        TreeMap<String, Double> result = new TreeMap<String, Double>();
        for (String doc : docs) {
            //System.out.println("sumTermIdfs for "+doc+": "+sumTermIdfs(terms, doc));
            result.put(doc, sumTermIdfs(terms, doc));
        }
        return result;
    }

    public void printUrlIndex() {
        for (Map.Entry<String, Integer> entry : urlIndex.entrySet()) {
            String url = entry.getKey();
            Integer count = entry.getValue();
            System.out.println(url + " " + count);
        }
    }

    public void printWordIndex() {
        //System.out.println(wordIndex.entrySet().toString());
        for (Map.Entry<String, HashMap<String, Integer>> entry : wordIndex.entrySet()) {

            String word = entry.getKey();
            HashMap<String, Integer> indexEntry = entry.getValue();
            System.out.println(word);
            if (indexEntry != null) {
                for (Map.Entry<String, Integer> entry2 : indexEntry.entrySet()) {
                    String url = entry2.getKey();
                    Integer count = entry2.getValue();
                    System.out.println("\t" + url + " " + count);
                }
            }

        }
    }

    // PRIVATE

    private HashMap<String, HashMap<String, Integer>>
            wordIndex = new HashMap<String, HashMap<String, Integer>>();
    private HashMap<String, Integer>
            urlIndex = new HashMap<String, Integer>();

    private void indexOne(String url, String html) {
        if (html != null) {
            System.out.println("indexing " + url);
            String[] words = getWords(html);
            HashMap<String, Integer> freqs = getWordFrequencies(words);

            // Update wordIndex

            for (Map.Entry<String, Integer> entry : freqs.entrySet()) {
                String word = entry.getKey();
                Integer count = entry.getValue();

                HashMap<String, Integer> indexEntry = wordIndex.get(word);
                if (indexEntry == null) {
                    indexEntry = new HashMap<String, Integer>();
                    wordIndex.put(word, indexEntry);
                }
                wordIndex.get(word).put(url, count);
            }

            // Update urlIndex
            int max = -1;
            Collection<Integer> values = freqs.values();
            for (Integer i : values) {
                if (i > max)
                    max = i;
            }
            urlIndex.put(url, max);
        }
        else {
            System.out.println("ERROR: no html for " + url);
        }
    }

    private String getHtml(String url) {
        String content = null;
        try {
            content = Request.Get(url).execute().returnContent().asString();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return content;
    }

    private String[] getWords(String html) {
        String text = Jsoup.clean(html, Whitelist.none()).toLowerCase();
        String[] words = text.split("\\W+");
        return words;
    }

    private String[] getLinks(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        Elements links = doc.select("a[href]");
        String[] results = new String[links.size()];
        int i = 0;
        for (Element e : links) {
            results[i] = e.attr("abs:href");
            i++;
        }
        //System.out.println(i + " links found.");
        return results;
    }

    private HashMap<String, Integer> getWordFrequencies(String[] words) {
        HashMap<String, Integer> freqs = new HashMap<String, Integer>();
        for (String w : words) {
            Integer count = freqs.get(w);
            if (count == null) {
                freqs.put(w,1);
            }
            else {
                freqs.put(w, ++count);
            }
        }
        return freqs;
    }

    // SEARCH

    private double inverseDocumentFrequency(String term) {
        double totalDocs = urlIndex.size();
        double docsWithTerm = wordIndex.get(term).size() + 1;
        return Math.log(totalDocs / docsWithTerm);
    }

    private double termFrequency(String term, String url){
        double freq = 0.0;
        HashMap<String, Integer> entry = wordIndex.get(term);
        if (entry != null) {
            if (entry.get(url) != null) {
                freq = entry.get(url);
            }
        }
        double maxFreq = urlIndex.get(url);
        return (freq / maxFreq);
    }

    private double tfIdf(String term, String url) {
        double tf = termFrequency(term, url);
        double idf = inverseDocumentFrequency(term);
        return tf * idf;
    }

    private double sumTermIdfs(String[] terms, String url) {
        double result = 0.0;
        for (String term : terms) {
            //System.out.println("url "+url+", term "+term+" tfIdf: "+tfIdf(term, url));
            result += tfIdf(term, url);
        }
        return result;
    }

    private Set<String> getUrlSetForTerm(String term) {
        HashMap<String, Integer> entry = wordIndex.get(term);
        if (entry != null) {
            return entry.keySet();
        }
        return null;
    }

    private ArrayList<Set<String>> getUrlSets(String[] terms) {
        ArrayList<Set<String>> result = new ArrayList<Set<String>>();
        for (String term : terms) {
            result.add(getUrlSetForTerm(term));
        }
        return result;
    }

    private Set<String> getUnion(String[] terms) {
        ArrayList<Set<String>> sets = getUrlSets(terms);
        if (sets == null)
            return null;
        Set<String> result = new HashSet<String>();
        for (Set<String> set : sets) {
            result.addAll(set);
        }
        return result;
    }

    // private Set<String> getIntersection(String[] terms)
}


// have to think about types vs. kind of seeing what type you end up with
   // don't have to worry when type changes as you develop

// nice to see how map and reduce map to imperative style
  // and various other functional to imperative differences

// you really do think in the programming language of choice
  // this is especially prominent when language are drastically different (i.e. functional vs imperative)
  // in FP you think in 'aggregate' terms - how to do it all at once, rather than one by one