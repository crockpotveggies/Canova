/*
 *
 *  *
 *  *  * Copyright 2015 Skymind,Inc.
 *  *  *
 *  *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *  *    you may not use this file except in compliance with the License.
 *  *  *    You may obtain a copy of the License at
 *  *  *
 *  *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *  *
 *  *  *    Unless required by applicable law or agreed to in writing, software
 *  *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  *    See the License for the specific language governing permissions and
 *  *  *    limitations under the License.
 *  *
 *
 */

package org.canova.codec.reader;

import static org.junit.Assert.*;

import org.canova.api.conf.Configuration;
import org.canova.api.records.reader.SequenceRecordReader;
import org.canova.api.split.FileSplit;
import org.canova.api.util.ClassPathResource;
import org.canova.api.writable.ArrayWritable;
import org.canova.api.writable.Writable;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Adam Gibson
 */
public class CodecReaderTest {
    @Test
    public void testCodecReader() throws Exception {
        File file = new ClassPathResource("fire_lowres.mp4").getFile();
        SequenceRecordReader reader = new CodecRecordReader();
        Configuration conf = new Configuration();
        conf.set(CodecRecordReader.RAVEL, "true");
        conf.set(CodecRecordReader.START_FRAME, "160");
        conf.set(CodecRecordReader.TOTAL_FRAMES, "500");
        conf.set(CodecRecordReader.ROWS, "80");
        conf.set(CodecRecordReader.COLUMNS, "46");
        reader.initialize(new FileSplit(file));
        reader.setConf(conf);
        assertTrue(reader.hasNext());
        Collection<Collection<Writable>> record = reader.sequenceRecord();
        System.out.println(record.size());

        Iterator<Collection<Writable>> it = record.iterator();
        Collection<Writable> first = it.next();
        System.out.println(first);

        //Expected size: 80x46x3
        assertEquals(1, first.size());
        assertEquals(80 * 46 * 3, ((ArrayWritable)first.iterator().next()).length());
    }


    @Test
    public void testViaDataInputStream() throws Exception {

        File file = new ClassPathResource("fire_lowres.mp4").getFile();
        SequenceRecordReader reader = new CodecRecordReader();
        Configuration conf = new Configuration();
        conf.set(CodecRecordReader.RAVEL, "true");
        conf.set(CodecRecordReader.START_FRAME, "160");
        conf.set(CodecRecordReader.TOTAL_FRAMES, "500");
        conf.set(CodecRecordReader.ROWS, "80");
        conf.set(CodecRecordReader.COLUMNS, "46");

        Configuration conf2 = new Configuration(conf);

        reader.initialize(new FileSplit(file));
        reader.setConf(conf);
        assertTrue(reader.hasNext());
        Collection<Collection<Writable>> expected = reader.sequenceRecord();


        SequenceRecordReader reader2 = new CodecRecordReader();
        reader2.setConf(conf2);

        DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
        Collection<Collection<Writable>> actual = reader2.sequenceRecord(null, dataInputStream);

        assertEquals(expected,actual);
    }

}
