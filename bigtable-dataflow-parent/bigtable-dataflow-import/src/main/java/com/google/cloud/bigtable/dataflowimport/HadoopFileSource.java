/*
 * Copyright (C) 2015 The Google Cloud Dataflow Hadoop Library Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.bigtable.dataflowimport;

import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.KvCoder;
import com.google.cloud.dataflow.sdk.coders.VoidCoder;
import com.google.cloud.dataflow.sdk.io.BoundedSource;
import com.google.cloud.dataflow.sdk.io.Read;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;

/**
 * Part of third party contribution to Google Dataflow Java SDK
 * (http://github.com/GoogleCloudPlatform/DataflowJavaSDK/tree/master/contrib/hadoop). Repackaged
 * with change.
 *
 * <p>A {@code BoundedSource} for reading files resident in a Hadoop filesystem using a
 * Hadoop file-based input format.
 *
 * <p>To read a {@link com.google.cloud.dataflow.sdk.values.PCollection} of
 * {@link com.google.cloud.dataflow.sdk.values.KV} key-value pairs from one or more
 * Hadoop files, use {@link com.google.cloud.bigtable.dataflowimport.HadoopFileSource#from} to specify the path(s) of the files to
 * read, the Hadoop {@link org.apache.hadoop.mapreduce.lib.input.FileInputFormat}, the
 * key class and the value class.
 *
 * <p>A {@code HadoopFileSource} can be read from using the
 * {@link com.google.cloud.dataflow.sdk.io.Read} transform. For example:
 *
 * <pre>
 * {@code
 * HadoopFileSource<K, V> source = HadoopFileSource.from(path, MyInputFormat.class,
 *   MyKey.class, MyValue.class);
 * PCollection<KV<MyKey, MyValue>> records = Read.from(mySource);
 * }
 * </pre>
 *
 * <p>The {@link com.google.cloud.bigtable.dataflowimport.HadoopFileSource#readFrom} method is a convenience method
 * that returns a read transform. For example:
 *
 * <pre>
 * {@code
 * PCollection<KV<MyKey, MyValue>> records = HadoopFileSource.readFrom(path,
 *   MyInputFormat.class, MyKey.class, MyValue.class);
 * }
 * </pre>
 *
 * Implementation note: Since Hadoop's {@link org.apache.hadoop.mapreduce.lib.input.FileInputFormat}
 * determines the input splits, this class extends {@link com.google.cloud.dataflow.sdk.io.BoundedSource} rather than
 * {@link com.google.cloud.dataflow.sdk.io.OffsetBasedSource}, since the latter
 * dictates input splits.
 *
 * @param <K> The type of keys to be read from the source.
 * @param <V> The type of values to be read from the source.
 * @author sduskis
 * @version $Id: $Id
 */
public class HadoopFileSource<K, V> extends BoundedSource<KV<K, V>> {
  private static final long serialVersionUID = 0L;
  private static final Logger SOURCE_LOG = LoggerFactory.getLogger(HadoopFileSource.class);

  private static final long MIN_BUNDLE_SIZE_BYTES = 100 * (1 << 20);

  // Work-around to suppress confusing warning and stack traces by gcs-connector.
  // See setIsRemoteFileFromLaunchSite() for more information. This variable
  // must be static.
  private static boolean isRemoteFileFromLaunchSite;

  private final String filepattern;
  private final Class<? extends FileInputFormat<K, V>> formatClass;
  private final Class<K> keyClass;
  private final Class<V> valueClass;
  private final SerializableSplit serializableSplit;
  private final Coder<KV<K, V>> overrideOutputCoder;
  // Deserializer configuration that cannot be put in core-site.xml. E.g., hbase.import.version
  // needs to be dynamically set depending on the HBase sequence file's format.
  private final Map<String, String> serializationProperties;

  /**
   * Creates a {@code Read} transform that will read from an {@code HadoopFileSource}
   * with the given file name or pattern ("glob") using the given Hadoop
   * {@link org.apache.hadoop.mapreduce.lib.input.FileInputFormat},
   * with key-value types specified by the given key class and value class.
   *
   * @param filepattern a {@link java.lang.String} object.
   * @param formatClass a {@link java.lang.Class} object.
   * @param keyClass a {@link java.lang.Class} object.
   * @param valueClass a {@link java.lang.Class} object.
   * @return a {@link com.google.cloud.dataflow.sdk.io.Read.Bounded} object.
   */
  public static <K, V, T extends FileInputFormat<K, V>> Read.Bounded<KV<K, V>> readFrom(
      String filepattern, Class<T> formatClass, Class<K> keyClass, Class<V> valueClass) {
    return Read.from(from(filepattern, formatClass, keyClass, valueClass));
  }

  /**
   * Creates a {@code HadoopFileSource} that reads from the given file name or pattern ("glob")
   * using the given Hadoop {@link org.apache.hadoop.mapreduce.lib.input.FileInputFormat},
   * with key-value types specified by the given key class and value class.
   *
   * @param filepattern a {@link java.lang.String} object.
   * @param formatClass a {@link java.lang.Class} object.
   * @param keyClass a {@link java.lang.Class} object.
   * @param valueClass a {@link java.lang.Class} object.
   * @return a {@link com.google.cloud.bigtable.dataflowimport.HadoopFileSource} object.
   */
  public static <K, V, T extends FileInputFormat<K, V>> HadoopFileSource<K, V> from(
      String filepattern, Class<T> formatClass, Class<K> keyClass, Class<V> valueClass) {
    return (HadoopFileSource<K, V>)
        new HadoopFileSource<K, V>(filepattern, formatClass, keyClass, valueClass);
  }

  /**
   * Creates a {@code HadoopFileSource} that reads from the given file name or pattern ("glob")
   * using the given Hadoop {@link org.apache.hadoop.mapreduce.lib.input.FileInputFormat},
   * with key-value types specified by the given key class and value class. The {@code source}
   * also returns a user-provided output coder and passes on additonal configuration parameters
   * to the deserializer.
   *
   * @param filepattern a {@link java.lang.String} object.
   * @param formatClass a {@link java.lang.Class} object.
   * @param keyClass a {@link java.lang.Class} object.
   * @param valueClass a {@link java.lang.Class} object.
   * @param overrideOutputCoder a {@link com.google.cloud.dataflow.sdk.coders.Coder} object.
   * @param serializationProperties a {@link java.util.Map} object.
   * @return a {@link com.google.cloud.bigtable.dataflowimport.HadoopFileSource} object.
   */
  public static <K, V, T extends FileInputFormat<K, V>> HadoopFileSource<K, V> from(
      String filepattern, Class<T> formatClass, Class<K> keyClass, Class<V> valueClass,
      Coder<KV<K, V>> overrideOutputCoder, Map<String, String> serializationProperties) {
    return new HadoopFileSource<K, V>(filepattern, formatClass, keyClass, valueClass,
        null /** serializableSplit **/, overrideOutputCoder, serializationProperties);
  }

  /**
   * A workaround to suppress confusing warnings and stack traces when this class
   * is instantiated off the cloud for files on google cloud storage.
   *
   * <p>If a dataflow job using files on Google Cloud Storage is launched off the cloud
   * (e.g., from user's desktop), dataflow causes the source to access the files UNNECESSARILY
   * from the local host, which is bound to fail because gcs-connector is already configured to
   * use the Google Compute Engine instance's authentication mechanism, which won't work for access
   * from off the cloud. The resulting warnings are very confusing because it happens right before
   * dataflow task-staging, which may take a long time. To the user the program may appear to have
   * failed fatally.
   *
   * <p>When {@code isRemoteFile} is {@code true}, this class would not try to access
   * google cloud storage from off the cloud, sidestepping the problem. When the program is staged
   * on cloud this flag is not carried over and file accesses would be allowed.
   *
   * @param isRemoteFile a boolean.
   */
  public static void setIsRemoteFileFromLaunchSite(boolean isRemoteFile) {
    isRemoteFileFromLaunchSite = isRemoteFile;
  }

  /**
   * Create a {@code HadoopFileSource} based on a file or a file pattern specification.
   */
  private HadoopFileSource(String filepattern,
      Class<? extends FileInputFormat<K, V>> formatClass, Class<K> keyClass,
      Class<V> valueClass) {
    this(filepattern, formatClass, keyClass, valueClass, null /** serializableSplit**/,
        null /** overrideOutputCoder**/, ImmutableMap.<String, String>of());
  }

  /**
   * Create a {@code HadoopFileSource} based on a single Hadoop input split, which won't be
   * split up further.
   */
  private HadoopFileSource(String filepattern,
      Class<? extends FileInputFormat<K, V>> formatClass, Class<K> keyClass,
      Class<V> valueClass, SerializableSplit serializableSplit, Coder<KV<K, V>> overrideOutputCoder,
      Map<String, String> serializationProperties) {
    this.filepattern = filepattern;
    this.formatClass = formatClass;
    this.keyClass = keyClass;
    this.valueClass = valueClass;
    this.serializableSplit = serializableSplit;
    this.overrideOutputCoder = overrideOutputCoder;
    this.serializationProperties = serializationProperties == null
        ? ImmutableMap.<String, String>of() : ImmutableMap.copyOf(serializationProperties);
  }

  /**
   * <p>Getter for the field <code>filepattern</code>.</p>
   *
   * @return a {@link java.lang.String} object.
   */
  public String getFilepattern() {
    return filepattern;
  }

  /**
   * <p>Getter for the field <code>formatClass</code>.</p>
   *
   * @return a {@link java.lang.Class} object.
   */
  public Class<? extends FileInputFormat<K, V>> getFormatClass() {
    return formatClass;
  }

  /**
   * <p>Getter for the field <code>keyClass</code>.</p>
   *
   * @return a {@link java.lang.Class} object.
   */
  public Class<K> getKeyClass() {
    return keyClass;
  }

  /**
   * <p>Getter for the field <code>valueClass</code>.</p>
   *
   * @return a {@link java.lang.Class} object.
   */
  public Class<V> getValueClass() {
    return valueClass;
  }

  /** {@inheritDoc} */
  @Override
  public void validate() {
    Preconditions.checkNotNull(filepattern,
        "need to set the filepattern of a HadoopFileSource");
    Preconditions.checkNotNull(formatClass,
        "need to set the format class of a HadoopFileSource");
    Preconditions.checkNotNull(keyClass,
        "need to set the key class of a HadoopFileSource");
    Preconditions.checkNotNull(valueClass,
        "need to set the value class of a HadoopFileSource");
  }

  /** {@inheritDoc} */
  @Override
  public List<? extends BoundedSource<KV<K, V>>> splitIntoBundles(long desiredBundleSizeBytes,
      PipelineOptions options) throws Exception {
    if (serializableSplit == null) {
      // Dataflow can send a really small number desiredBundleSizeBytes, as low as 1. 100 MB chunks
      // seem to be a good floor.
      long bundleSizeBytes = Math.max(desiredBundleSizeBytes, MIN_BUNDLE_SIZE_BYTES);

      // Each file in the path described by the filePattern is broken down into splits reflecting
      // the desiredBundleSizeBytes.  The list will be ordered by filename and starting location
      List<InputSplit> splits = computeSplits(bundleSizeBytes);
      SOURCE_LOG.info("Got " + splits.size() + " splits.");

      // Randomize the order of the splits. This improves Bigtable performance, since splits will
      // reach different nodes. Without this shuffle, the splits will likely hit very few nodes
      // since the splits have contiguous row ranges.
      //
      // With the shuffle, the ranges across the splits will be disjointed. Each split will now have
      // a higher likelihood of reaching a different node than its neighbors.
      Collections.shuffle(splits);

      return Lists.transform(splits,
          new Function<InputSplit, BoundedSource<KV<K, V>>>() {
        @Nullable @Override
        public BoundedSource<KV<K, V>> apply(@Nullable InputSplit inputSplit) {
          return new HadoopFileSource<K, V>(filepattern, formatClass, keyClass,
              valueClass, new SerializableSplit(inputSplit), overrideOutputCoder,
              serializationProperties);
        }
      });
    } else {
      return ImmutableList.of(this);
    }
  }

  private FileInputFormat<K, V> createFormat(Job job) throws IOException, IllegalAccessException,
      InstantiationException {
    Path path = new Path(filepattern);
    FileInputFormat.addInputPath(job, path);
    return formatClass.newInstance();
  }

  private List<InputSplit> computeSplits(long desiredBundleSizeBytes) throws IOException,
      IllegalAccessException, InstantiationException {
    Job job = Job.getInstance(getDeserializerConfiguration());
    FileInputFormat.setMinInputSplitSize(job, desiredBundleSizeBytes);
    FileInputFormat.setMaxInputSplitSize(job, desiredBundleSizeBytes);
    return createFormat(job).getSplits(job);
  }

  /** {@inheritDoc} */
  @Override
  public BoundedReader<KV<K, V>> createReader(PipelineOptions options) throws IOException {
    this.validate();

    if (serializableSplit == null) {
      return new HadoopFileReader<>(this, filepattern, formatClass, serializationProperties);
    } else {
      return new HadoopFileReader<>(this, filepattern, formatClass,
          serializableSplit.getSplit(), serializationProperties);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Coder<KV<K, V>> getDefaultOutputCoder() {
    if (overrideOutputCoder != null) {
      return overrideOutputCoder;
    }
    return KvCoder.of(getDefaultCoder(keyClass), getDefaultCoder(valueClass));
  }

  @SuppressWarnings("unchecked")
  private <T> Coder<T> getDefaultCoder(Class<T> c) {
    if (Writable.class.isAssignableFrom(c)) {
      Class<? extends Writable> writableClass = (Class<? extends Writable>) c;
      return (Coder<T>) WritableCoder.of(writableClass);
    } else if (Void.class.equals(c)) {
      return (Coder<T>) VoidCoder.of();
    }
    // TODO: how to use registered coders here?
    throw new IllegalStateException("Cannot find coder for " + c);
  }

  private static Configuration getHadoopConfigWithOverrides(Map<String, String> overrides) {
    Configuration configuration = new Configuration();
    for (Map.Entry<String, String> entry : overrides.entrySet()) {
      configuration.set(entry.getKey(), entry.getValue());
    }
    return configuration;
  }

  Configuration getDeserializerConfiguration() {
    return getHadoopConfigWithOverrides(serializationProperties);
  }

  // BoundedSource

  /** {@inheritDoc} */
  @Override
  public long getEstimatedSizeBytes(PipelineOptions options) {
    if (isRemoteFileFromLaunchSite) {
      return 0;
    }
    long size = 0;
    try {
      Job job = Job.getInstance(getDeserializerConfiguration()); // new instance
      for (FileStatus st : listStatus(createFormat(job), job)) {
        size += st.getLen();
      }
      return size;
    } catch (IOException | NoSuchMethodException | InvocationTargetException
        | IllegalAccessException | InstantiationException e) {
      // ignore, and return 0
      SOURCE_LOG.error("Got exception while trying to getEstimatedSizeBytes().", e);
      return 0;
    }
  }

  @SuppressWarnings("unchecked")
  private List<FileStatus> listStatus(FileInputFormat<K, V> format,
      JobContext jobContext) throws NoSuchMethodException, InvocationTargetException,
      IllegalAccessException {
    // FileInputFormat#listStatus is protected, so call using reflection
    Method listStatus = FileInputFormat.class.getDeclaredMethod("listStatus", JobContext.class);
    listStatus.setAccessible(true);
    return (List<FileStatus>) listStatus.invoke(format, jobContext);
  }

  /** {@inheritDoc} */
  @Override
  public boolean producesSortedKeys(PipelineOptions options) throws Exception {
    return false;
  }

  static class HadoopFileReader<K, V> extends BoundedSource.BoundedReader<KV<K, V>> {

    private final BoundedSource<KV<K, V>> source;
    private final String filepattern;
    private final Class<? extends FileInputFormat<K, V>> formatClass;
    private final Map<String, String> serializationProperties;

    private FileInputFormat<K, V> format;
    private TaskAttemptContext attemptContext;
    private List<InputSplit> splits;
    private ListIterator<InputSplit> splitsIterator;
    private Configuration conf;
    private RecordReader<K, V> currentReader;
    private KV<K, V> currentPair;
    private volatile boolean done = false;

    /**
     * Create a {@code HadoopFileReader} based on a file or a file pattern specification.
     */
    public HadoopFileReader(BoundedSource<KV<K, V>> source, String filepattern,
        Class<? extends FileInputFormat<K, V>> formatClass,
        Map<String, String> serializationProperties) {
      this(source, filepattern, formatClass, null, serializationProperties);
    }

    /**
     * Create a {@code HadoopFileReader} based on a single Hadoop input split.
     */
    public HadoopFileReader(BoundedSource<KV<K, V>> source, String filepattern,
        Class<? extends FileInputFormat<K, V>> formatClass, InputSplit split,
        Map<String, String> serializationProperties) {
      this.source = source;
      this.filepattern = filepattern;
      this.formatClass = formatClass;
      if (split != null) {
        this.splits = ImmutableList.of(split);
        this.splitsIterator = splits.listIterator();
      }
      this.serializationProperties = serializationProperties == null
          ? ImmutableMap.<String, String> of() : ImmutableMap.copyOf(serializationProperties);
    }

    @Override
    public boolean start() throws IOException {
      Job job = Job.getInstance(getDeserializerConfiguration());
      Path path = new Path(filepattern);
      FileInputFormat.addInputPath(job, path);

      try {
        this.format = formatClass.newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        throw new IOException("Cannot instantiate file input format " + formatClass, e);
      }
      this.attemptContext = new TaskAttemptContextImpl(job.getConfiguration(), new TaskAttemptID());

      if (splitsIterator == null) {
        this.splits = format.getSplits(job);
        this.splitsIterator = splits.listIterator();
      }
      this.conf = job.getConfiguration();
      return advance();
    }

    @Override
    public boolean advance() throws IOException {
      try {
        if (currentReader != null && currentReader.nextKeyValue()) {
          currentPair = nextPair();
          return true;
        } else {
          while (splitsIterator.hasNext()) {
            // advance the reader and see if it has records
            InputSplit nextSplit = splitsIterator.next();
            RecordReader<K, V> reader = format.createRecordReader(nextSplit, attemptContext);
            if (currentReader != null) {
              currentReader.close();
            }
            currentReader = reader;
            currentReader.initialize(nextSplit, attemptContext);
            if (currentReader.nextKeyValue()) {
              currentPair = nextPair();
              return true;
            }
            currentReader.close();
            currentReader = null;
          }
          // either no next split or all readers were empty
          currentPair = null;
          done = true;
          return false;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
      }
    }

    @VisibleForTesting
    Configuration getDeserializerConfiguration() {
      return getHadoopConfigWithOverrides(serializationProperties);
    }

    private KV<K, V> nextPair() throws IOException, InterruptedException {
      return KV.of(
        cloneIfWritable(currentReader.getCurrentKey()),
        cloneIfWritable(currentReader.getCurrentValue()));
    }

    @SuppressWarnings("unchecked")
    private <T> T cloneIfWritable(T value) {
      if (value instanceof Writable) {
        return (T) WritableUtils.clone((Writable) value, conf);
      }
      return value;
    }

    @Override
    public KV<K, V> getCurrent() throws NoSuchElementException {
      if (currentPair == null) {
        throw new NoSuchElementException();
      }
      return currentPair;
    }

    @Override
    public void close() throws IOException {
      if (currentReader != null) {
        currentReader.close();
        currentReader = null;
      }
      currentPair = null;
    }

    @Override
    public BoundedSource<KV<K, V>> getCurrentSource() {
      return source;
    }

    // BoundedReader

    @Override
    public Double getFractionConsumed() {
      if (currentReader == null) {
        return 0.0;
      }
      if (splits.isEmpty()) {
        return 1.0;
      }
      int index = splitsIterator.previousIndex();
      int numReaders = splits.size();
      if (index == numReaders) {
        return 1.0;
      }
      double before = 1.0 * index / numReaders;
      double after = 1.0 * (index + 1) / numReaders;
      Double fractionOfCurrentReader = getProgress();
      if (fractionOfCurrentReader == null) {
        return before;
      }
      return before + fractionOfCurrentReader * (after - before);
    }

    private Double getProgress() {
      try {
        return (double) currentReader.getProgress();
      } catch (IOException | InterruptedException e) {
        SOURCE_LOG.error("Problem with HadoopFileSource.getProcess()", e);
        return null;
      }
    }

    @Override
    public final long getSplitPointsRemaining() {
      // This source does not currently support dynamic work rebalancing, so remaining
      // parallelism is always 1.
      return done ? 0 : 1;
    }

    @Override
    public BoundedSource<KV<K, V>> splitAtFraction(double fraction) {
      // Not yet supported. To implement this, the sizes of the splits should be used to
      // calculate the remaining splits that constitute the given fraction, then a
      // new source backed by those splits should be returned.
      return null;
    }
  }

  /**
   * A wrapper to allow Hadoop {@link org.apache.hadoop.mapreduce.InputSplit}s to be
   * serialized using Java's standard serialization mechanisms. Note that the InputSplit
   * has to be Writable (which most are).
   */
  public static class SerializableSplit implements Externalizable {
    private static final long serialVersionUID = 0L;

    private InputSplit split;

    public SerializableSplit() {}

    public SerializableSplit(InputSplit split) {
      Preconditions.checkArgument(split instanceof Writable, "Split is not writable: "
          + split);
      this.split = split;
    }

    public InputSplit getSplit() {
      return split;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
      out.writeUTF(split.getClass().getCanonicalName());
      ((Writable) split).write(out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      String className = in.readUTF();
      try {
        split = (InputSplit) Class.forName(className).newInstance();
        ((Writable) split).readFields(in);
      } catch (InstantiationException | IllegalAccessException e) {
        throw new IOException(e);
      }
    }
  }
}
