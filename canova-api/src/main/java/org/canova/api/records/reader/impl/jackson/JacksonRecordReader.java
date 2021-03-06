package org.canova.api.records.reader.impl.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.canova.api.conf.Configuration;
import org.canova.api.io.data.Text;
import org.canova.api.io.labels.PathLabelGenerator;
import org.canova.api.records.reader.RecordReader;
import org.canova.api.split.FileSplit;
import org.canova.api.split.InputSplit;
import org.canova.api.writable.Writable;

import java.io.*;
import java.net.URI;
import java.util.*;

/**
 * RecordReader using Jackson.<br>
 * <b>Design for this record reader</b>:<br>
 * - Support for JSON, XML and YAML: <i>one record per file only</i>, via Jackson ObjectMapper:<br>
 * <ul style="list-style-type:none">
 * <li>- JSON: new ObjectMapper(new JsonFactory())</li>
 * <li>- YAML: new ObjectMapper(new YAMLFactory()) (requires jackson-dataformat-yaml dependency)</li>
 * <li>- XML: new ObjectMapper(new XmlFactory()) (requires jackson-dataformat-xml dependency)</li>
 * </ul>
 * - User provides a list of fields to load, using {@link FieldSelection}. This complicates configuration for simple structures
 * (user has to specify every field to load), however this allows us to parse files where:
 * <ul style="list-style-type:none">
 * <li>- The fields in the json/xml/yaml is not in a consistent order (for example, JSON makes no guarantees about order).
 *     The order of output fields is provided via the FieldSelection object.</li>
 * <li>- Fields may be missing in some files (output will include an (optionally) specified writable for the missing value,
 *     defined again in FieldSelection)</li>
 * <li>- The fields in the json/yaml/xml files may have arbitrary nested structure: For example, {@code a: b: c: d: someValue}</li>
 * </ul>
 * - Optional support for appending a label based on the path of the file, using {@link PathLabelGenerator}<br>
 * - Support for shuffling of records (with an optional RNG seed)<br>
 *
 * @author Alex Black
 */
public class JacksonRecordReader implements RecordReader {

    private static final TypeReference<Map<String,Object>> typeRef = new TypeReference<Map<String, Object>>(){};

    private FieldSelection selection;
    private ObjectMapper mapper;
    private boolean shuffle;
    private long rngSeed;
    private PathLabelGenerator labelGenerator;
    private int labelPosition;
    private InputSplit is;
    private Random r;

    private URI[] uris;
    private int cursor = 0;

    public JacksonRecordReader(FieldSelection selection, ObjectMapper mapper){
        this(selection, mapper, false);
    }

    public JacksonRecordReader(FieldSelection selection, ObjectMapper mapper, boolean shuffle){
        this(selection, mapper, shuffle, System.currentTimeMillis(), null);
    }

    public JacksonRecordReader(FieldSelection selection, ObjectMapper mapper, boolean shuffle, long rngSeed,
                               PathLabelGenerator labelGenerator) {
        this(selection, mapper, shuffle, rngSeed, labelGenerator, -1);
    }

    public JacksonRecordReader(FieldSelection selection, ObjectMapper mapper, boolean shuffle, long rngSeed,
                               PathLabelGenerator labelGenerator, int labelPosition){
        this.selection = selection;
        this.mapper = mapper;
        this.shuffle = shuffle;
        this.rngSeed = rngSeed;
        if(shuffle) r = new Random(rngSeed);
        this.labelGenerator = labelGenerator;
        this.labelPosition = labelPosition;
    }

    @Override
    public void initialize(InputSplit split) throws IOException, InterruptedException {
        if(split instanceof FileSplit) throw new UnsupportedOperationException("Cannot use JacksonRecordReader with FileSplit");
        this.uris = split.locations();
        if(shuffle){
            List<URI> list = Arrays.asList(uris);
            Collections.shuffle(list,r);
            uris = list.toArray(new URI[uris.length]);
        }
    }

    @Override
    public void initialize(Configuration conf, InputSplit split) throws IOException, InterruptedException {
        initialize(split);
    }

    @Override
    public Collection<Writable> next() {
        if(uris == null) throw new IllegalStateException("URIs are null. Not initialized?");
        if(!hasNext()) throw new NoSuchElementException("No next element");

        URI uri = uris[cursor++];
        String fileAsString;
        try{
            fileAsString = FileUtils.readFileToString(new File(uri.toURL().getFile()));
        } catch(IOException e){
            throw new RuntimeException("Error reading URI file",e);
        }

        return readValues(uri, fileAsString);

    }

    @Override
    public boolean hasNext() {
        return cursor < uris.length;
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        cursor = 0;
        if(shuffle){
            List<URI> list = Arrays.asList(uris);
            Collections.shuffle(list,r);
            uris = list.toArray(new URI[uris.length]);
        }
    }

    @Override
    public Collection<Writable> record(URI uri, DataInputStream dataInputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(dataInputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while( (line = br.readLine()) != null){
            sb.append(line).append("\n");
        }

        return readValues(uri,sb.toString());
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void setConf(Configuration conf) {

    }

    @Override
    public Configuration getConf() {
        return null;
    }


    private Collection<Writable> readValues(URI uri, String fileContents){
        List<Writable> out = new ArrayList<>();
        List<String[]> paths = selection.getFieldPaths();
        List<Writable> valueIfMissing = selection.getValueIfMissing();

        Map<String,Object> map;
        try{
            map = mapper.readValue(fileContents, typeRef);
        } catch(IOException e){
            throw new RuntimeException("Error parsing file",e);
        }

        //Now, extract out values...
        for(int i=0; i<paths.size(); i++ ){
            //First: check if we should insert the label here...
            if(i == labelPosition && labelGenerator != null){
                out.add(labelGenerator.getLabelForPath(uri));
            }

            String[] currPath = paths.get(i);
            String value = null;
            Map<String,Object> currMap = map;
            for( int j=0; j<currPath.length; j++ ){
                if(currMap.containsKey(currPath[j])) {
                    Object o = currMap.get(currPath[j]);
                    if(j == currPath.length -1){
                        //Expect to get the final value
                        if(o instanceof String) {
                            value = (String) o;
                        } else if(o instanceof Number){
                            value = o.toString();
                        } else {
                            throw new IllegalStateException("Expected to find String on path " + Arrays.toString(currPath)
                                    + ", found " + o.getClass() + " with value " + o);
                        }
                    } else {
                        //Expect to get a map...
                        if(o instanceof Map){
                            currMap = (Map<String,Object>)o;
                        }
                    }
                } else {
                    //Not found
                    value = null;
                    break;
                }
            }

            Writable outputWritable;
            if(value == null){
                outputWritable = valueIfMissing.get(i);
            } else {
                outputWritable = new Text(value);
            }
            out.add(outputWritable);
        }

        //Edge case: might want label as the last value
        if((labelPosition >= paths.size() || labelPosition == -1) && labelGenerator != null ){
            out.add(labelGenerator.getLabelForPath(uri));
        }

        return out;
    }
}
