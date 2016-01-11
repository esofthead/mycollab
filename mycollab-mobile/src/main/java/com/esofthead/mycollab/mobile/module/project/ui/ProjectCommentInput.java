/**
 * This file is part of mycollab-mobile.
 *
 * mycollab-mobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mycollab-mobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mycollab-mobile.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.esofthead.mycollab.mobile.module.project.ui;

import com.esofthead.mycollab.common.domain.CommentWithBLOBs;
import com.esofthead.mycollab.common.i18n.GenericI18Enum;
import com.esofthead.mycollab.common.service.CommentService;
import com.esofthead.mycollab.core.utils.ImageUtil;
import com.esofthead.mycollab.mobile.ui.IconConstants;
import com.esofthead.mycollab.mobile.ui.MobileAttachmentUtils;
import com.esofthead.mycollab.mobile.ui.TempFileFactory;
import com.esofthead.mycollab.module.ecm.service.ResourceService;
import com.esofthead.mycollab.module.file.AttachmentUtils;
import com.esofthead.mycollab.module.project.CurrentProjectVariables;
import com.esofthead.mycollab.spring.ApplicationContextUtil;
import com.esofthead.mycollab.vaadin.AppContext;
import com.esofthead.mycollab.vaadin.ui.NotificationUtil;
import com.esofthead.mycollab.vaadin.ui.ReloadableComponent;
import com.vaadin.server.StreamVariable;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.easyuploads.*;
import org.vaadin.viritin.layouts.MHorizontalLayout;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * @author MyCollab Ltd.
 * @since 4.4.0
 */
@SuppressWarnings("unused")
public class ProjectCommentInput extends VerticalLayout {
    private static final Logger LOG = LoggerFactory.getLogger(ProjectCommentInput.class.getName());
    private static final long serialVersionUID = 8118887310759503892L;

    private TextField commentInput;

    private String type;
    private String typeId;
    private Integer extraTypeId;
    private ReloadableComponent component;

    private FileBuffer receiver;
    private MultiUpload uploadField;
    private Map<String, File> fileStores;

    private ResourceService resourceService;

    private int currentPollInterval;

    private CssLayout statusWrapper;

    public ProjectCommentInput(ReloadableComponent component, String typeVal, Integer extraTypeIdVal) {
        resourceService = ApplicationContextUtil.getSpringBean(ResourceService.class);
        this.setWidth("100%");
        this.setStyleName("comment-input");

        type = typeVal;
        extraTypeId = extraTypeIdVal;
        this.component = component;

        currentPollInterval = UI.getCurrent().getPollInterval();
        constructUI();
    }

    private void constructUI() {
        statusWrapper = new CssLayout();
        statusWrapper.setWidth("100%");
        statusWrapper.setStyleName("upload-status-wrap");
        this.addComponent(statusWrapper);

        MHorizontalLayout inputWrapper = new MHorizontalLayout().withWidth("100%");

        this.prepareUploadField();

        commentInput = new TextField();
        commentInput.setInputPrompt(AppContext.getMessage(GenericI18Enum.M_NOTE_INPUT_PROMPT));

        Button postBtn = new Button(AppContext.getMessage(GenericI18Enum.M_BUTTON_SEND), new Button.ClickListener() {
            @Override
            public void buttonClick(ClickEvent clickEvent) {
                final CommentWithBLOBs comment = new CommentWithBLOBs();
                comment.setComment(commentInput.getValue());
                comment.setCreatedtime(new GregorianCalendar().getTime());
                comment.setCreateduser(AppContext.getUsername());
                comment.setSaccountid(AppContext.getAccountId());
                comment.setType(type.toString());
                comment.setTypeid("" + typeId);
                comment.setExtratypeid(extraTypeId);

                final CommentService commentService = ApplicationContextUtil.getSpringBean(CommentService.class);
                int commentId = commentService.saveWithSession(comment, AppContext.getUsername());

                String attachmentPath = AttachmentUtils.getCommentAttachmentPath(type, AppContext.getAccountId(),
                        CurrentProjectVariables.getProjectId(), typeId, commentId);
                if (!"".equals(attachmentPath)) {
                    saveContentsToRepo(attachmentPath);
                }

                // save success, clear comment area and load list comments again
                commentInput.setValue("");
                statusWrapper.removeAllComponents();
                component.reload();
            }
        });
        postBtn.setStyleName("submit-btn");
        postBtn.setWidthUndefined();
        inputWrapper.with(uploadField, commentInput, postBtn).expand(commentInput);
        this.addComponent(inputWrapper);
        this.setExpandRatio(inputWrapper, 1.0f);
    }

    private void prepareUploadField() {
        receiver = createReceiver();

        uploadField = new MultiUpload();
        uploadField.setButtonCaption("");
        uploadField.setImmediate(true);

        MultiUploadHandler handler = new MultiUploadHandler() {
            private LinkedList<ProgressBar> indicators;

            @Override
            public void streamingStarted(StreamVariable.StreamingStartEvent event) {
            }

            @Override
            public void streamingFinished(StreamVariable.StreamingEndEvent event) {
                String fileName = event.getFileName();
                int index = fileName.lastIndexOf(".");
                if (index > 0) {
                    String fileExt = fileName.substring(index + 1, fileName.length());
                    fileName = MobileAttachmentUtils.ATTACHMENT_NAME_PREFIX + System.currentTimeMillis() + "." + fileExt;
                }

                if (!indicators.isEmpty()) {
                    statusWrapper.replaceComponent(indicators.remove(0), createAttachmentRow(fileName));
                }

                if (indicators.size() == 0) {
                    UI.getCurrent().setPollInterval(currentPollInterval);
                }

                File file = receiver.getFile();

                receiveFile(file, fileName, event.getMimeType(), event.getBytesReceived());
                receiver.setValue(null);
            }

            @Override
            public void streamingFailed(StreamVariable.StreamingErrorEvent event) {
                if (!indicators.isEmpty()) {
                    Label uploadResult = new Label("Upload failed! File: " + event.getFileName());
                    uploadResult.setStyleName("upload-status");
                    statusWrapper.replaceComponent(indicators.remove(0), uploadResult);
                }
            }

            @Override
            public void onProgress(StreamVariable.StreamingProgressEvent event) {
                long readBytes = event.getBytesReceived();
                long contentLength = event.getContentLength();
                float f = (float) readBytes / (float) contentLength;
                indicators.get(0).setValue(f);
            }

            @Override
            public OutputStream getOutputStream() {
                MultiUpload.FileDetail next = uploadField.getPendingFileNames().iterator().next();
                return receiver.receiveUpload(next.getFileName(), next.getMimeType());
            }

            @Override
            public void filesQueued(Collection<MultiUpload.FileDetail> pendingFileNames) {
                UI.getCurrent().setPollInterval(500);
                if (indicators == null) {
                    indicators = new LinkedList<>();
                }
                for (MultiUpload.FileDetail f : pendingFileNames) {
                    ProgressBar pi = new ProgressBar();
                    pi.setValue(0f);
                    pi.setStyleName("upload-progress");
                    pi.setWidth("100%");
                    statusWrapper.addComponent(pi);
                    pi.setEnabled(true);
                    pi.setVisible(true);
                    indicators.add(pi);
                }
            }

            @Override
            public boolean isInterrupted() {
                return false;
            }
        };
        uploadField.setHandler(handler);
    }

    private Component createAttachmentRow(String fileName) {
        final HorizontalLayout uploadSucceedLayout = new HorizontalLayout();
        uploadSucceedLayout.setWidth("100%");
        Label uploadResult = new Label(fileName);
        uploadResult.setWidth("100%");
        uploadSucceedLayout.addComponent(uploadResult);
        uploadSucceedLayout.setExpandRatio(uploadResult, 1.0f);

        Button removeAttachment = new Button(
                "<span aria-hidden=\"true\" data-icon=\""
                        + IconConstants.DELETE + "\"></span>",
                new Button.ClickListener() {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public void buttonClick(ClickEvent event) {
                        statusWrapper.removeComponent(uploadSucceedLayout);
                    }

                });
        removeAttachment.setHtmlContentAllowed(true);
        removeAttachment.setStyleName("link");
        uploadSucceedLayout.addComponent(removeAttachment);
        uploadSucceedLayout.setStyleName("upload-succeed-layout");
        uploadSucceedLayout.setSpacing(true);
        return uploadSucceedLayout;
    }

    private void saveContentsToRepo(String attachmentPath) {
        if (MapUtils.isNotEmpty(fileStores)) {
            for (Map.Entry<String, File> entry : fileStores.entrySet()) {
                try {
                    String fileName = entry.getKey();
                    File file = entry.getValue();
                    String fileExt = "";
                    int index = fileName.lastIndexOf(".");
                    if (index > 0) {
                        fileExt = fileName.substring(index + 1, fileName.length());
                    }

                    if ("jpg".equalsIgnoreCase(fileExt) || "png".equalsIgnoreCase(fileExt)) {
                        try {
                            BufferedImage bufferedImage = ImageIO.read(file);

                            int imgHeight = bufferedImage.getHeight();
                            int imgWidth = bufferedImage.getWidth();

                            BufferedImage scaledImage;

                            float scale;
                            float destWidth = 974;
                            float destHeight = 718;

                            float scaleX = Math.min(destHeight / imgHeight, 1);
                            float scaleY = Math.min(destWidth / imgWidth, 1);
                            scale = Math.min(scaleX, scaleY);
                            scaledImage = ImageUtil.scaleImage(bufferedImage,
                                    scale);

                            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                            ImageIO.write(scaledImage, fileExt, outStream);

                            resourceService.saveContent(MobileAttachmentUtils.constructContent(fileName, attachmentPath),
                                    AppContext.getUsername(), new ByteArrayInputStream(outStream.toByteArray()), AppContext.getAccountId());
                        } catch (IOException e) {
                            LOG.error("Error in upload file", e);
                            resourceService.saveContent(MobileAttachmentUtils.constructContent(fileName, attachmentPath),
                                    AppContext.getUsername(), new FileInputStream(file), AppContext.getAccountId());
                        }
                    } else {
                        resourceService.saveContent(MobileAttachmentUtils.constructContent(fileName, attachmentPath),
                                AppContext.getUsername(), new FileInputStream(file), AppContext.getAccountId());
                    }

                } catch (FileNotFoundException e) {
                    LOG.error("Error when attach content in UI", e);
                }
            }
        }
    }

    protected FileBuffer createReceiver() {
        FileBuffer receiver = new FileBuffer(UploadField.FieldType.FILE) {
            private static final long serialVersionUID = 1L;

            @Override
            public FileFactory getFileFactory() {
                return new TempFileFactory();
            }
        };
        receiver.setDeleteFiles(false);
        return receiver;
    }

    public void setTypeAndId(final String typeid) {
        this.typeId = typeid;
    }

    public void receiveFile(File file, String fileName, String mimeType, long length) {
        if (fileStores == null) {
            fileStores = new HashMap<>();
        }
        if (fileStores.containsKey(fileName)) {
            NotificationUtil.showWarningNotification("File " + fileName + " is already existed.");
        } else {
            LOG.debug("Store file " + fileName + " in path " + file.getAbsolutePath() + " is exist: " + file.exists());
            fileStores.put(fileName, file);
        }
    }

}
