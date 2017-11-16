package hust.tools.csc.score;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

import hust.tools.csc.ngram.NGramModel;
import hust.tools.csc.util.ConfusionSet;
import hust.tools.csc.util.Dictionary;
import hust.tools.csc.util.FormatConvert;
import hust.tools.csc.util.Sentence;
import hust.tools.csc.util.Sequence;

/**
 *<ul>
 *<li>Description: 噪音通道模型抽象类
 *<li>Company: HUST
 *<li>@author Sonly
 *<li>Date: 2017年10月24日
 *</ul>
 */
public abstract class AbstractNoisyChannelModel implements NoisyChannelModel {
	
	protected int order = 3;
	protected int beamSize = 150;
	protected ConfusionSet confusionSet;
	protected NGramModel nGramModel;
	
	public AbstractNoisyChannelModel(ConfusionSet confusionSet, NGramModel nGramModel) {
		this.confusionSet = confusionSet;
		this.nGramModel = nGramModel;
	}
	
	/**
	 * 根据给定句子，给出得分最高的前size个候选句子
	 * @param sentence	待搜索的原始句子
	 * @param size		搜索束的大小
	 * @param locations	错误字的位置
	 * @return			给出得分最高的前size个候选句子
	 */
	protected ArrayList<Sentence> beamSearch(ConfusionSet confusionSet, int beamSize, Sentence sentence, ArrayList<Integer> locations) {
		
		Queue<Sequence> prev = new PriorityQueue<>(beamSize);
	    Queue<Sequence> next = new PriorityQueue<>(beamSize);
	    Queue<Sequence> tmp;
	    prev.add(new Sequence(sentence, getSourceModelLogScore(sentence)));
	    	
	    for(int index : locations) {//遍历每一个单字词
	    	String character = sentence.getToken(index);
    		
    		if(character == null || !FormatConvert.isHanZi(character))
    			continue;
    		
    		HashSet<String> tmpPronCands = confusionSet.getSimilarityPronunciations(character);
//    		HashSet<String> tmpShapeCands = confusionSet.getSimilarityShapes(character);
    		HashSet<String> tmpCands = new HashSet<>();
    		if(tmpPronCands != null)
    			tmpCands.addAll(tmpPronCands);
//    		if(tmpShapeCands != null)
//    			tmpCands.addAll(tmpShapeCands);
    		tmpCands.add(character);
	    	
	    	int sz = Math.min(beamSize, prev.size());
	    	for(int sc = 0; prev.size() > 0 && sc < sz; sc++) {
	    		Sequence top = prev.remove();
	    		next.add(top);
//	    		log.info(top.getSentence()+"'Score = " + top.getScore());
	    		//音近、形近候选字获取并合并
	    				
	    		Iterator<String> iterator = tmpCands.iterator();
	    		while(iterator.hasNext()) {
	    			String candCharacter = iterator.next();
	    			Sentence candSen = top.getSentence().setToken(index, candCharacter);
	    			double score = getSourceModelLogScore(candSen) * getChannelModelLogScore(sentence, index, candCharacter, tmpCands);
//	    			log.info(candSen+"'Score = " + score);
	    			next.add(new Sequence(candSen, score));
	    		}
	        }

	        prev.clear();
	        tmp = prev;
	        prev = next;
	        next = tmp;
	      }
	    
	    ArrayList<Sentence> result = new ArrayList<>();
	    int num = Math.min(5, prev.size());

	    for (int i = 0; i < num; i++)
	      result.add(prev.remove().getSentence());
		
		return result;
	}
	
	
	/**
	 * 返回候选句子noisy channel model：p(s|c)*p(c)中的p(c)
	 * @param candidate	候选句子
	 * @return	p(c)
	 */
	public abstract double getSourceModelLogScore(Sentence candidate);
	
	/**
	 * 返回noisy channel model：p(s|c)*p(c)中的p(s|c)
	 * @param candidate	候选句子
	 * @return	p(s|c)
	 */
	public abstract double getChannelModelLogScore(Sentence sentence, int location, String candidate,  HashSet<String> cands);
	
	/**
	 * 计算候选字的总个数
	 * @param cands			候选字集
	 * @param dictionary	字串与计数的映射
	 * @return				所有候选字的总个数
	 */
	protected double getTotalCharcterCount(HashSet<String> cands, Dictionary dictionary) {
		double total = 1.0;
		Iterator<String> iterator = cands.iterator();
		while(iterator.hasNext()) {
			String cand = iterator.next();
			if(dictionary.contains(cand))
				total += dictionary.getCount(cand);
		}
		
		return total;
	}
	
	/**
	 * 计算所有候选字与其前后邻居组成的bigram的计数的乘积，第一个字/最后一个字只考虑与其后/前一个字组成的bigram
	 * @param sentence		句子，用于确定前后邻居
	 * @param index			给定候选字在句子中应处的位置
	 * @param cands			候选字集
	 * @param dictionary	字串与计数的映射
	 * @return				总数之积
	 */
	protected double getTotalPrefixAndSuffixBigramCount(Sentence sentence, int index, HashSet<String> cands, Dictionary dictionary) {
		double totalPre = 1.0;
		double totalNext = 1.0;
		Iterator<String> iterator = cands.iterator();
		while(iterator.hasNext()) {
			String cand = iterator.next();
			String preToken = "";
			String nextToken = "";
			
			if(index > 0)
				preToken = sentence.getToken(index - 1);
			if(index < sentence.size() - 1)
				nextToken = sentence.getToken(index + 1);
			
			String preBigram = preToken + cand;
			String nextBigram = cand + nextToken;
			if(dictionary.contains(preBigram))
				totalPre += dictionary.getCount(preBigram);
			if(dictionary.contains(nextBigram))
				totalNext += dictionary.getCount(nextBigram);
		}
		
		return totalPre * totalNext;
		
	}
	
	/**
	 * 返回连续的单字词的最大长度，并将孤立的单字词位置索引剔除
	 * @param words	词组
	 * @return		连续的单字词的最大长度
	 */
	protected int maxContinueSingleWordsLength(ArrayList<Integer> locations) {		
		if(locations.size() < 2) 
			return locations.size();
		
		int max = 0;
		int len = 1;
		for(int i = 1; i < locations.size(); i++) {
			if(locations.get(i) - locations.get(i - 1) == 1)
				len++;
			else {
				max = max > len ? max : len;
				len = 1;
			}
		}
		
		max = max > len ? max : len;
		return max;
	}
	
	/**
	 * 返回单个字的词在句子中的索引
	 * @param words	句子分词后的词
	 * @return		单个字的词在句子中的位置
	 */
	protected ArrayList<Integer> locationsOfSingleWords(ArrayList<String> words) {
		ArrayList<Integer> locations = new ArrayList<>();
		int index = 0;
		for(String word : words) {
			if(word.length() == 1) {
				if(FormatConvert.isHanZi(word)) {
					locations.add(index);
				}
			}
			
			index += word.length();
		}
		
		return locations;
	}
	
	/**
	 * 根据n元切分匹配方法确定错误字的位置
	 * @param sentence			待处理的句子
	 * @param errorLocations	错误字在句子中的位置索引列表
	 */
	protected ArrayList<Integer> getErrorLocationsBySIMD(Dictionary dictionary, Sentence sentence) {
		ArrayList<Integer> errorLocations = new ArrayList<>();
		ArrayList<String> bigrams = generateBigrams(sentence.toString().split(""));
		
		//可能的错误位置， 当前bigram与下一个bigram中有不存在与字典的，设置当前bigram的第二个字为可能出错的字
		for(int index = 0; index < bigrams.size() - 1; index++) {
			String currentBigram = bigrams.get(index);
			String nextBigram = bigrams.get(index + 1);
			
			if(!(dictionary.contains(currentBigram) && dictionary.contains(nextBigram))) {
				String wrong = sentence.getToken(index + 1);
				//非汉字不考虑
				if(FormatConvert.isHanZi(wrong))
					errorLocations.add(index + 1);
			}
		}//end for
		
		return errorLocations;
	}
	
	/**
	 * 将给定句子切分成bigrams
	 * @param input	待切分的句子
	 * @return		bigrams
	 */
	private ArrayList<String> generateBigrams(String[] input)  {
		ArrayList<String> output = new ArrayList<>();
		
		for(int i = 0; i < input.length - 2 + 1; i++) {
			String[] ngrams = new String[2];
			for(int j = i, index = 0; j < i + 2; j++, index++)
				ngrams[index] = input[j];

			String bigram = "";
			for(String ch : ngrams)
				bigram += ch;
			
			output.add(bigram);
		}//end for
		
		return output;
	}

}
