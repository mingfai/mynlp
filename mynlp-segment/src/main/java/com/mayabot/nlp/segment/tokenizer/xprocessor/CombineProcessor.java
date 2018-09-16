/*
 * Copyright 2018 mayabot.com authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mayabot.nlp.segment.tokenizer.xprocessor;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mayabot.nlp.fst.FST;
import com.mayabot.nlp.fst.FstMatcher;
import com.mayabot.nlp.fst.FstNode;
import com.mayabot.nlp.segment.WordpathProcessor;
import com.mayabot.nlp.segment.common.BaseMynlpComponent;
import com.mayabot.nlp.segment.dictionary.Nature;
import com.mayabot.nlp.segment.dictionary.NatureAttribute;
import com.mayabot.nlp.segment.dictionary.core.CoreDictionary;
import com.mayabot.nlp.segment.wordnet.Vertex;
import com.mayabot.nlp.segment.wordnet.Wordnet;
import com.mayabot.nlp.segment.wordnet.Wordpath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mayabot.nlp.segment.wordnet.Vertex.hasNature;


/**
 * 各种场景的合并场景
 * 1. 数量词  数字和量词。例如 72kg
 * 2. 连接符号\单词和数字
 * 3. 书名号 《》
 * 4. 双引号
 *
 * @author jimichan
 */
public class CombineProcessor extends BaseMynlpComponent implements WordpathProcessor {

    private final int x_cluster_wordid;
    private final NatureAttribute x_cluster_nature = NatureAttribute.create(Nature.x, 100000);

    private FST<Vertex> shuLiang;

    private boolean enableShuLiang = false;

    private HashSet<String> QuantityUnit = Sets.newHashSet(
            "公里 米 kg mm 平方 斤 公斤 斗 升 尺 寸 丈".split(" ")
    );

    /**
     * 单词和数字和连接符号连接在一起
     */
    private boolean enableConnectionSymbol = true;
    //
    private Pattern connectionSymbolPattern2 = Pattern.compile("[_\\-\\w\\d][_\\-\\w\\d]+");

    private boolean enableEmail = true;

    private Pattern emailPattern = Pattern.compile("\\w+(?:\\.\\w+)*@\\w+(?:(?:\\.\\w+)+)");


    private boolean enableBookName = true;

    private Pattern bookNamePattern = Pattern.compile("《.+?》");

    private boolean enableDoubleQuotation = true;

    private Pattern DoubleQuotation = Pattern.compile("[\"“].+?[\"”]");

    /**
     * 构造函数
     *
     * @param coreDictionary
     */
    @Inject
    public CombineProcessor(CoreDictionary coreDictionary) {
        x_cluster_wordid = coreDictionary.getWordID(CoreDictionary.TAG_CLUSTER);

        shuLiang = make(fst -> {
            FstNode<Vertex> shuzi = fst.start().to("shuzi",
                    (index, vertex) -> hasNature(vertex, Nature.m));

            FstNode<Vertex> danwei = shuzi.to("danwei",
                    (index, vertex) -> hasNature(vertex, Nature.q) ||
                            QuantityUnit.contains(vertex.realWord().toLowerCase())
            );

            danwei.to("$", (index, vertex) -> true);
        });
    }

    private FST<Vertex> make(Consumer<FST<Vertex>> x) {
        FST<Vertex> fst = new FST<>();
        x.accept(fst);
        return fst;
    }


    public HashSet<String> getQuantityUnit() {
        return QuantityUnit;
    }

    @Override
    public Wordpath process(Wordpath wordPath) {
        final Wordnet wordnet = wordPath.getWordnet();

        ArrayList<Vertex> inputList = Lists.newArrayList(wordPath.getBestPathWithBE());


        if (enableShuLiang) {
            run(shuLiang, inputList, wordPath);
        }

        if (enableConnectionSymbol) {

            run(connectionSymbolPattern2, wordnet, wordPath);
        }

        if (enableEmail) {
            run(emailPattern, wordnet, wordPath);
        }

        if (enableBookName) {
            Matcher matcher = bookNamePattern.matcher(wordnet);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                int wordID = x_cluster_wordid;
                Vertex bookName = wordnet.row(start + 1).getOrCrete(end - start - 2);
                if (bookName.wordID == -1) {
                    //FIXME 这里不产生wordID有没有问题
                    //主要给切分子词做一些准备
                    bookName.setNatureAttribute(NatureAttribute.create1000(Nature.n));
                }

                Vertex vertex = wordPath.combine(start, end - start);
                vertex.setWordInfo(wordID, CoreDictionary.TAG_CLUSTER, NatureAttribute.create1000(Nature.n));

            }
        }

        if (enableDoubleQuotation) {
            Matcher matcher = DoubleQuotation.matcher(wordnet);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                int wordID = x_cluster_wordid;
                Vertex bookName = wordnet.row(start + 1).getOrCrete(end - start - 2);
                if (bookName.wordID == -1) {
                    //FIXME 这里不产生wordID有没有问题
                    //主要给切分子词做一些准备
                    bookName.setNatureAttribute(NatureAttribute.create1000(Nature.x));
                }

                Vertex vertex = wordPath.combine(start, end - start);
                vertex.setWordInfo(wordID, CoreDictionary.TAG_CLUSTER, x_cluster_nature);
            }
        }

        return wordPath;
    }

    public CombineProcessor setEnableShuLiang(boolean enableShuLiang) {
        this.enableShuLiang = enableShuLiang;
        return this;
    }

    private void run(Pattern pattern, Wordnet wordnet, Wordpath wordPath) {
        Matcher matcher = pattern.matcher(wordnet);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            boolean cut = wordPath.willCutOtherWords(start, end - start);
            if (cut) {
                continue;
            }

            Vertex vertex = wordPath.combine(start, end - start);
            int wordID = x_cluster_wordid;
            vertex.setWordInfo(wordID, CoreDictionary.TAG_CLUSTER, x_cluster_nature);
        }
    }

    private void run(FST<Vertex> fst, ArrayList<Vertex> inputList, Wordpath wordPath) {

        FstMatcher<Vertex, Vertex> m = fst.newMatcher(inputList);


        while (m.find()) {

            int from = m.getStartObj().getRowNum();

            int len = m.getEndObj().getRowNum() + m.getEndObj().getLength() - from;

            Vertex vertex = wordPath.combine(from, len);

            int wordID = x_cluster_wordid;
            vertex.setWordInfo(wordID, CoreDictionary.TAG_CLUSTER, x_cluster_nature);
        }
    }

}
