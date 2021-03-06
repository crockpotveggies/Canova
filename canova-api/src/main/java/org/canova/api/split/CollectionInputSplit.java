package org.canova.api.split;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;

/**
 * A simple InputSplit based on a collection of URIs
 *
 * @author Alex Black
 */
public class CollectionInputSplit implements InputSplit {

    private URI[] uris;

    public CollectionInputSplit(Collection<URI> list){
        this.uris = list.toArray(new URI[list.size()]);
    }

    @Override
    public long length() {
        return uris.length;
    }

    @Override
    public URI[] locations() {
        return uris;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public double toDouble() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public float toFloat() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int toInt() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public long toLong() {
        throw new UnsupportedOperationException("Not supported");
    }
}
