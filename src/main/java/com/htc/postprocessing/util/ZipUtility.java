package com.htc.postprocessing.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class ZipUtility {
	private static final int BUFFER_SIZE = 4096;

	public void archivedFiles(List<File> listFiles, String destZipFile) throws FileNotFoundException, IOException {
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destZipFile));
		for (File file : listFiles) {
			if (file.isDirectory()) {
				zipDirectory(file, file.getName(), zos);
			} else {
				zipFile(file, zos);
			}
		}
		zos.flush();
		zos.close();
	}

	private void zipDirectory(File folder, String parentFolder, ZipOutputStream zipOutputStream)
			throws FileNotFoundException, IOException {
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				zipDirectory(file, parentFolder + "/" + file.getName(), zipOutputStream);
				continue;
			}
			zipOutputStream.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
			long bytesRead = 0;
			byte[] bytesIn = new byte[BUFFER_SIZE];
			int read = 0;
			while ((read = bis.read(bytesIn)) != -1) {
				zipOutputStream.write(bytesIn, 0, read);
				bytesRead += read;
			}
			zipOutputStream.closeEntry();
			bis.close();
		}
	}

	private void zipFile(File file, ZipOutputStream zipOutputStream) throws FileNotFoundException, IOException {
		zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
		long bytesRead = 0;
		byte[] bytesIn = new byte[BUFFER_SIZE];
		int read = 0;
		while ((read = bis.read(bytesIn)) != -1) {
			zipOutputStream.write(bytesIn, 0, read);
			bytesRead += read;
		}
		bis.close();
		zipOutputStream.closeEntry();
	}
}