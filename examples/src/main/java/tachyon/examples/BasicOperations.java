/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package tachyon.examples;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tachyon.Constants;
import tachyon.TachyonURI;
import tachyon.Version;
import tachyon.client.ClientContext;
import tachyon.client.ClientOptions;
import tachyon.client.TachyonStorageType;
import tachyon.client.UnderStorageType;
import tachyon.client.file.FileInStream;
import tachyon.client.file.FileOutStream;
import tachyon.client.file.TachyonFile;
import tachyon.client.file.TachyonFileSystem;
import tachyon.conf.TachyonConf;
import tachyon.thrift.BlockInfoException;
import tachyon.thrift.FileAlreadyExistException;
import tachyon.thrift.FileDoesNotExistException;
import tachyon.thrift.InvalidPathException;
import tachyon.util.CommonUtils;
import tachyon.util.FormatUtils;

public class BasicOperations implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(Constants.LOGGER_TYPE);

  // private access to the reinitializer of ClientContext
  private static ClientContext.ReinitializerAccesser sReinitializerAccesser =
      new ClientContext.ReinitializerAccesser() {
        @Override
        public void receiveAccess(ClientContext.PrivateReinitializer access) {
          sReinitializer = access;
        }
      };
  private static ClientContext.PrivateReinitializer sReinitializer;

  private final TachyonURI mMasterLocation;
  private final TachyonURI mFilePath;
  private final ClientOptions mClientOptions;
  private final int mNumbers = 20;

  public BasicOperations(TachyonURI masterLocation, TachyonURI filePath,
      TachyonStorageType storageType, UnderStorageType underStorageType) {
    mMasterLocation = masterLocation;
    mFilePath = filePath;
    mClientOptions = new ClientOptions.Builder(ClientContext.getConf())
        .setStorageTypes(storageType, underStorageType).build();
  }

  @Override
  public Boolean call() throws Exception {
    TachyonConf tachyonConf = ClientContext.getConf();
    tachyonConf.set(Constants.MASTER_HOSTNAME, mMasterLocation.getHost());
    tachyonConf.set(Constants.MASTER_PORT, Integer.toString(mMasterLocation.getPort()));
    if (sReinitializer == null) {
      ClientContext.accessReinitializer(sReinitializerAccesser);
    }
    sReinitializer.reinitializeWithConf(tachyonConf);
    TachyonFileSystem tFS = TachyonFileSystem.get();
    long fileId = createFile(tFS);
    writeFile(fileId);
    return readFile(tFS, fileId);
  }

  private long createFile(TachyonFileSystem tachyonFileSystem)
      throws IOException, BlockInfoException, FileAlreadyExistException, InvalidPathException {
    LOG.debug("Creating file...");
    long startTimeMs = CommonUtils.getCurrentMs();
    long fileId = tachyonFileSystem.createEmptyFile(mFilePath, mClientOptions);
    LOG.info(FormatUtils.formatTimeTakenMs(startTimeMs, "createFile with fileId " + fileId));
    return fileId;
  }

  private void writeFile(long fileId)
      throws IOException, BlockInfoException, FileAlreadyExistException, InvalidPathException {
    ByteBuffer buf = ByteBuffer.allocate(mNumbers * 4);
    buf.order(ByteOrder.nativeOrder());
    for (int k = 0; k < mNumbers; k ++) {
      buf.putInt(k);
    }

    buf.flip();
    LOG.debug("Writing data...");
    buf.flip();

    long startTimeMs = CommonUtils.getCurrentMs();
    FileOutStream os = new FileOutStream(fileId, mClientOptions);
    os.write(buf.array());
    os.close();

    LOG.info(FormatUtils.formatTimeTakenMs(startTimeMs, "writeFile to file " + mFilePath));
  }

  private boolean readFile(TachyonFileSystem tachyonFileSystem, long fileId)
      throws IOException, FileDoesNotExistException {
    boolean pass = true;
    LOG.debug("Reading data...");
    TachyonFile file = new TachyonFile(fileId);
    final long startTimeMs = CommonUtils.getCurrentMs();
    FileInStream is = tachyonFileSystem.getInStream(file, mClientOptions);
    ByteBuffer buf = ByteBuffer.allocate((int) is.remaining());
    is.read(buf.array());
    buf.order(ByteOrder.nativeOrder());
    for (int k = 0; k < mNumbers; k ++) {
      pass = pass && (buf.getInt() == k);
    }
    is.close();

    LOG.info(FormatUtils.formatTimeTakenMs(startTimeMs, "readFile file " + mFilePath));
    return pass;
  }

  public static void main(String[] args) throws IllegalArgumentException {
    if (args.length != 4) {
      System.out.println("java -cp target/tachyon-" + Version.VERSION
          + "-jar-with-dependencies.jar tachyon.examples.BasicOperations"
          + " <TachyonMasterAddress> <FilePath> <TachyonStorageType(STORE|NO_STORE)>"
          + " <UnderStorageType(PERSIST|NO_PERSIST)>");
      System.exit(-1);
    }

    Utils.runExample(new BasicOperations(new TachyonURI(args[0]), new TachyonURI(args[1]),
        TachyonStorageType.valueOf(args[2]), UnderStorageType.valueOf(args[3])));
  }
}
