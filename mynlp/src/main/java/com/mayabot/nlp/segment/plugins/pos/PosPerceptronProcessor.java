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

package com.mayabot.nlp.segment.plugins.pos;

import com.google.common.collect.Lists;
import com.mayabot.nlp.injector.Singleton;
import com.mayabot.nlp.segment.Nature;
import com.mayabot.nlp.segment.WordpathProcessor;
import com.mayabot.nlp.segment.common.BaseSegmentComponent;
import com.mayabot.nlp.segment.wordnet.Vertex;
import com.mayabot.nlp.segment.wordnet.Wordpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 感知机词性分析计算
 *
 * @author jimichan
 */
@Singleton
public class PosPerceptronProcessor extends BaseSegmentComponent implements WordpathProcessor {

    private final PerceptronPosService perceptronPosService;

    public PosPerceptronProcessor(
            PerceptronPosService perceptronPosService
    ) {
        super(LEVEL4);
        this.perceptronPosService = perceptronPosService;
    }

    @Override
    public Wordpath process(Wordpath wordPath) {
        ArrayList<Vertex> vertices = Lists.newArrayList(wordPath.iteratorVertex());
        List<Nature> posList = perceptronPosService.posFromVertex(vertices);

        for (int i = 0; i < vertices.size(); i++) {
            Vertex vertex = vertices.get(i);
            Nature nr = posList.get(i);

            //人名识别，的优先级不能高于词性分析器。
            if (vertex.nature == null
                    || vertex.nature == Nature.newWord ||
                    vertex.nature == Nature.nr) {
                vertex.nature = nr;
            }
        }

        return wordPath;
    }
}