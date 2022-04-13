package com.htc.postprocessing.service;

import static com.htc.postprocessing.constant.PostProcessingConstant.ARCHIVE_DIRECTORY;
import static com.htc.postprocessing.constant.PostProcessingConstant.BANNER_DIRECTORY;
import static com.htc.postprocessing.constant.PostProcessingConstant.EMPTY_VALUE;
import static com.htc.postprocessing.constant.PostProcessingConstant.PRINT_DIRECTORY;
import static com.htc.postprocessing.constant.PostProcessingConstant.SPACE_DOT_PATTERN;
import static com.htc.postprocessing.constant.PostProcessingConstant.TRANSIT_BACKUP_DIRECTORY;
import static com.htc.postprocessing.constant.PostProcessingConstant.XML_TYPE;
import static com.htc.postprocessing.constant.PostProcessingConstant.TRANSIT_DIRECTORY;
import static com.htc.postprocessing.constant.PostProcessingConstant.EMPTY_SPACE;
import static com.htc.postprocessing.constant.PostProcessingConstant.SPACE_VALUE;
import static com.htc.postprocessing.constant.PostProcessingConstant.XML_EXTENSION;
import static com.htc.postprocessing.constant.PostProcessingConstant.PDF_EXTENSION;
import static com.htc.postprocessing.constant.PostProcessingConstant.PROCESSED_DIRECTORY;
import static com.htc.postprocessing.constant.PostProcessingConstant.PCL_EXTENSION;
import static com.htc.postprocessing.constant.PostProcessingConstant.DATETIME_INTERVAL;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.htc.postprocessing.constant.PostProcessingConstant;
import com.htc.postprocessing.scheduler.PostProcessingScheduler;
import com.htc.postprocessing.util.ZipUtility;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

/**
 * @author kumar.charanswain
 *
 */

@Service
public class PostProcessingService {

	Logger logger = LoggerFactory.getLogger(PostProcessingScheduler.class);

	@Value("${blob.accont.name.key}")
	private String connectionNameKey;

	@Value("${blob.container.name}")
	private String containerName;

	@Value("#{'${state.allow.type}'.split(',')}")
	private List<String> stateAllowType;

	@Value("#{'${page.type}'.split(',')}")
	private List<String> pageTypeList;

	@Value("${sheet.number.type}")
	private String sheetNbrType;

	@Value("${ftp.server.name}")
	private String ftpHostName;

	@Value("${ftp.server.port}")
	private int ftpPort;

	@Value("${ftp.server.username}")
	private String ftpUserName;

	@Value("${ftp.server.password}")
	private String ftpPassword;

	public String smartComPostProcessing() {
		String messageInfo = "smart comm post processing successfully";
		try {
			CloudBlobContainer container = containerInfo();
			String currentDate = currentDateTime();
			CloudBlobDirectory transitDirectory = getDirectoryName(container, TRANSIT_DIRECTORY,
					currentDate + "-" + PRINT_DIRECTORY);
			if (moveFileFromPrintToTransit(currentDate)) {
				messageInfo = processMetaDataInputFile(container, transitDirectory, currentDate);
			} else {
				messageInfo = "no file for post processing";
			}
		} catch (Exception exception) {
			logger.info("Exception:" + exception.getMessage());
			messageInfo = "error in copy file to blob directory";
		}
		return messageInfo;
	}

	private boolean moveFileFromPrintToTransit(String currentDate) {
		boolean moveSuccess = false;
		BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
		Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(PRINT_DIRECTORY);
		for (BlobItem blobItem : listBlobs) {
			BlobClient dstBlobClient = blobContainerClient
					.getBlobClient(TRANSIT_BACKUP_DIRECTORY + currentDate + "-" + blobItem.getName());
			BlobClient srcBlobClient = blobContainerClient.getBlobClient(blobItem.getName());
			dstBlobClient.copyFromUrl(srcBlobClient.getBlobUrl());
			srcBlobClient.delete();
			moveSuccess = true;
		}
		return moveSuccess;
	}

	public String processMetaDataInputFile(CloudBlobContainer container, CloudBlobDirectory transitDirectory,
			String currentDate) throws Exception {
		ConcurrentHashMap<String, List<String>> postProcessMap = new ConcurrentHashMap<String, List<String>>();
		String message = "smart comm post processing successfully";
		try {
			Iterable<ListBlobItem> blobList = transitDirectory.listBlobs();
			for (ListBlobItem blobItem : blobList) {
				String fileName = getFileNameFromBlobURI(blobItem.getUri()).replace(SPACE_VALUE, EMPTY_SPACE);
				File file = new File(fileName);
				CloudBlob cloudBlob = (CloudBlob) blobItem;
				cloudBlob.downloadToFile(file.getPath());
				boolean stateType = checkStateType(fileName);
				if (stateType) {
					if (StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(fileName), XML_TYPE)) {
						file.delete();
						continue;
					}
					String fileNameNoExt = FilenameUtils.removeExtension(fileName);
					String[] stateAndSheetNameList = StringUtils.split(fileNameNoExt, "_");
					String stateAndSheetName = stateAndSheetNameList.length > 0
							? stateAndSheetNameList[stateAndSheetNameList.length - 1]
							: "";
					prepareMap(postProcessMap, stateAndSheetName, fileName);
				} else if (checkPageType(fileName)) {
					if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(fileName))) {
						continue;
					}
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder = factory.newDocumentBuilder();
					Document document = builder.parse(new File(fileName));
					document.getDocumentElement().normalize();
					Element root = document.getDocumentElement();
					prepareMap(postProcessMap, getSheetNumber(root),
							StringUtils.replace(fileName, XML_EXTENSION, PDF_EXTENSION));
					file.delete();
				} else {
					logger.info("unable to process:invalid document type " + file);
					file.delete();
				}
			}
			if (postProcessMap.size() > 0) {
				message = mergePDF(postProcessMap, currentDate);
			} else {
				message = "unable to process :invalid state/document name";
			}
		} catch (Exception exception) {
			logger.info("Exception found:" + exception.getMessage());
		}
		return message;
	}

	private String getSheetNumber(Element root) {
		try {
			int sheetNumber = Integer.parseInt(root.getAttribute(sheetNbrType));
			if (sheetNumber <= 10) {
				return String.valueOf(sheetNumber);
			}
		} catch (Exception exception) {
			logger.info("Exception found:" + exception.getMessage());
		}
		return PostProcessingConstant.MULTIPAGE;
	}

	public BlobContainerClient getBlobContainerClient(String connectionNameKey, String containerName) {
		BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionNameKey)
				.buildClient();
		BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
		return blobContainerClient;
	}

	// post merge PDF
	public String mergePDF(ConcurrentHashMap<String, List<String>> postProcessMap, String currentDate)
			throws IOException {
		String message = "smart comm post processing successfully";
		List<String> fileNameList = new LinkedList<String>();
		for (String key : postProcessMap.keySet()) {
			try {
				PDFMergerUtility PDFMerger = new PDFMergerUtility();
				String claimNumber = "";
				String mergePdf = "";
				fileNameList = postProcessMap.get(key);
				String bannerFileName = getBannerPage(key);
				File bannerFile = new File(bannerFileName);
				PDFMerger.addSource(bannerFileName);
				Collections.sort(fileNameList);
				for (String fileName : fileNameList) {
					File file = new File(fileName);
					PDFMerger.addSource(file.getPath());
				}
				String fileNoExt = fileNameList.get(fileNameList.size() - 1).replaceFirst(SPACE_DOT_PATTERN,
						EMPTY_VALUE);
				if (fileNoExt.length() >= 24) {
					claimNumber = fileNoExt.substring(14, 24);
				} else {
					claimNumber = fileNoExt;
				}
				mergePdf = claimNumber + PDF_EXTENSION;
				PDFMerger.setDestinationFileName(mergePdf);
				PDFMerger.mergeDocuments();
				convertPDFToPCL(claimNumber, fileNameList, currentDate);
				bannerFile.delete();
				new File(mergePdf).delete();
			} catch (StorageException storageException) {
				logger.info("file not found for processing");
				if (fileNameList.size() > 0) {
					deleteFiles(fileNameList);
				}
				continue;
			} catch (Exception exception) {
				logger.info("Exception:" + exception.getMessage());
			}
		}
		fileTranserToFTPServer(ftpHostName, ftpPort, ftpUserName, ftpPassword, currentDate);
		return message;
	}

	// post processing PDF to PCL conversion
	public void convertPDFToPCL(String claimNumber, List<String> fileNameList, String currentDate) throws IOException {
		try {
			CloudBlobContainer container = containerInfo();
			CloudBlobDirectory processDirectory = getDirectoryName(container, TRANSIT_DIRECTORY, PROCESSED_DIRECTORY);
			String outputPclFile = claimNumber + PCL_EXTENSION;
			PDDocument document = new PDDocument();
			document.save(outputPclFile);

			File updatePCLFile = new File(outputPclFile);
			CloudBlockBlob processSubDirectoryBlob = processDirectory
					.getBlockBlobReference(claimNumber + PCL_EXTENSION);
			FileInputStream fileInputStream = new FileInputStream(updatePCLFile);
			processSubDirectoryBlob.upload(fileInputStream, updatePCLFile.length());

			fileInputStream.close();
			document.close();
			updatePCLFile.delete();
		} catch (Exception exception) {
			logger.info("Exception:" + exception.getMessage());
		}
	}

	public boolean checkStateType(String fileName) {
		for (String state : stateAllowType) {
			if (fileName.contains(state)) {
				return true;
			}
		}
		return false;
	}

	public int totalNumberPages(String fileName) throws IOException {
		PDDocument pdfDocument = PDDocument.load(new File(fileName));
		return pdfDocument.getPages().getCount();
	}

	public boolean checkPageType(String fileName) {
		for (String pageType : pageTypeList) {
			if (fileName.contains(pageType)) {
				return true;
			}
		}
		return false;
	}

	public void deleteFiles(List<String> fileNameList) throws IOException {
		for (String fileName : fileNameList) {
			File file = new File(fileName);
			file.delete();
		}
	}

	public void deleteFileList(List<File> fileNameList) throws IOException {
		for (File file : fileNameList) {
			file.delete();
		}
	}

	public void prepareMap(ConcurrentHashMap<String, List<String>> postProcessMap, String key, String fileName)
			throws IOException {
		if (postProcessMap.containsKey(key)) {
			List<String> existingFileNameList = postProcessMap.get(key);
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		} else {
			List<String> existingFileNameList = new ArrayList<String>();
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		}
	}

	public String getBannerPage(String key)
			throws URISyntaxException, StorageException, FileNotFoundException, IOException {
		CloudBlobContainer container = containerInfo();
		CloudBlobDirectory transitDirectory = getDirectoryName(container, BANNER_DIRECTORY, "");
		String bannerFileName = "Banner_" + key + PDF_EXTENSION;
		CloudBlockBlob blob = transitDirectory.getBlockBlobReference(bannerFileName);
		File source = new File(bannerFileName);
		blob.downloadToFile(source.getAbsolutePath());
		return bannerFileName;
	}

	public CloudBlobContainer containerInfo() {
		CloudBlobContainer container = null;
		try {
			CloudStorageAccount account = CloudStorageAccount.parse(connectionNameKey);
			CloudBlobClient serviceClient = account.createCloudBlobClient();
			container = serviceClient.getContainerReference(containerName);
		} catch (Exception exception) {
			logger.info("Exception:" + exception.getMessage());
		}
		return container;
	}

	public String currentDateTime() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat(DATETIME_INTERVAL);
		return dateFormat.format(date);
	}

	public CloudBlobDirectory getDirectoryName(CloudBlobContainer container, String directoryName,
			String subDirectoryName) throws URISyntaxException {
		CloudBlobDirectory cloudBlobDirectory = container.getDirectoryReference(directoryName);
		if (StringUtils.isBlank(subDirectoryName)) {
			return cloudBlobDirectory;
		}
		return cloudBlobDirectory.getDirectoryReference(subDirectoryName);
	}

	private String getFileNameFromBlobURI(URI uri) {
		String[] fileNameList = uri.toString().split(PostProcessingConstant.FILE_SEPARATION);
		Optional<String> fileName = Optional.empty();
		if (fileNameList.length > 1)
			fileName = Optional.ofNullable(fileNameList[fileNameList.length - 1]);
		return fileName.get();
	}

	// file transfer to ftp server
	private void fileTranserToFTPServer(String server, int port, String userName, String password, String currentDate)
			throws IOException {
		FTPClient ftpClient = new FTPClient();
		FileInputStream fileInputStream = null;
		List<File> fileNameList = new LinkedList<File>();
		String archiveFileName = currentDate + ARCHIVE_DIRECTORY;
		try {
			CloudBlobContainer container = containerInfo();
			CloudBlobDirectory transitDirectory = getDirectoryName(container, TRANSIT_DIRECTORY,
					currentDate + "-" + PRINT_DIRECTORY);
			Iterable<ListBlobItem> blobList = transitDirectory.listBlobs();
			for (ListBlobItem blobItem : blobList) {
				String fileName = getFileNameFromBlobURI(blobItem.getUri()).replace(SPACE_VALUE, EMPTY_SPACE);
				File file = new File(fileName);
				CloudBlob cloudBlob = (CloudBlob) blobItem;
				cloudBlob.downloadToFile(file.getPath());
				fileNameList.add(file);
			}
			ZipUtility zipUtility = new ZipUtility();
			zipUtility.archivedFiles(fileNameList, archiveFileName);
			ftpClient.connect(server, port);
			ftpClient.login(userName, password);
			ftpClient.enterLocalPassiveMode();
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			fileInputStream = new FileInputStream(archiveFileName);
			ftpClient.storeFile(archiveFileName, fileInputStream);
			fileInputStream.close();
			File archiveFile = new File(archiveFileName);
			archiveFile.delete();
			deleteFileList(fileNameList);
		} catch (Exception exception) {
			logger.info("Exception:" + exception.getMessage());
		} finally {
			ftpClient.disconnect();
		}
	}
}