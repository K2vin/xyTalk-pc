package xysoft.im.panels;

import xysoft.im.app.Launcher;
import xysoft.im.cache.UserCache;
import xysoft.im.components.Colors;
import xysoft.im.components.RCButton;
import xysoft.im.components.VerticalFlowLayout;
import xysoft.im.db.model.CurrentUser;
import xysoft.im.frames.MainFrame;
import xysoft.im.utils.AvatarUtil;
import xysoft.im.utils.DebugUtil;
import xysoft.im.utils.IconUtil;
import org.apache.commons.codec.binary.Base64;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.RescaleOp;
import java.io.*;
import static xysoft.im.app.Launcher.currentUserService;

public class ChangeAvatarPanel extends JPanel
{
    /**
	 * 修改头像面板
	 */
	private static final long serialVersionUID = 405309683586489977L;
	private static ChangeAvatarPanel context;
    private ImageAdjustLabel imageLabel;
    private RCButton okButton;
    private RCButton openButton;
    private JPanel contentPanel;
    private File selectedFile;
    private JLabel statusLabel;

    private int imageMaxWidth = 350;
    private int imageMaxHeight = 200;

    public ChangeAvatarPanel()
    {
        context = this;

        initComponents();
        initView();
        setListener();
    }

    private void openImage(File file)
    {
        try
        {
            BufferedImage image = ImageIO.read(file);

            int imageWidth = image.getWidth(null);
            int imageHeight = image.getHeight(null);
            if (imageWidth < 200 || imageHeight < 200)
            {
//                JOptionPane.showMessageDialog(MainFrame.getContext(), "建议使用 200 * 200 或更高分辨率的图像", "图像太low - , -!", JOptionPane.WARNING_MESSAGE);
//                return;
            }

            imageLabel.setImage(image);
            imageLabel.repaint();

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    private void initComponents()
    {

        Image avatar = new ImageIcon(AvatarUtil.createOrLoadUserAvatar(UserCache.CurrentUserName).getScaledInstance(200, 200, Image.SCALE_SMOOTH)).getImage();
        imageLabel = new ImageAdjustLabel(imageMaxWidth, imageMaxHeight, avatar);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(350, 300));
        //imageLabel.setBorder(new LineBorder(Colors.ITEM_SELECTED_LIGHT));

        //imageLabel.setIcon(new ImageIcon(AvatarUtil.createOrLoadUserAvatar(UserCache.CurrentUserName).getScaledInstance(200, 200, Image.SCALE_SMOOTH)));
        okButton = new RCButton("使用头像", Colors.MAIN_COLOR, Colors.MAIN_COLOR_DARKER, Colors.MAIN_COLOR_DARKER);
        okButton.setPreferredSize(new Dimension(100, 35));
        openButton = new RCButton("选择图片", Colors.MAIN_COLOR, Colors.MAIN_COLOR_DARKER, Colors.MAIN_COLOR_DARKER);
        openButton.setPreferredSize(new Dimension(100, 35));
        openButton.setToolTipText("点击上传本地头像");

        statusLabel = new JLabel();
        statusLabel.setText("头像应用成功");
        statusLabel.setForeground(Colors.FONT_GRAY_DARKER);
        statusLabel.setIcon(IconUtil.getIcon(this, "/image/check.png"));
        statusLabel.setVisible(false);

        contentPanel = new JPanel();
    }

    private void initView()
    {
        contentPanel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false));

        JPanel openPanel = new JPanel();
        openPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        openPanel.add(openButton);
        contentPanel.add(openPanel);
        contentPanel.add(imageLabel);
        contentPanel.add(okButton);
        contentPanel.add(statusLabel);

        add(contentPanel);
    }

    private void setListener()
    {
        openButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                chooseImage();
            }
        });

        okButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (okButton.isEnabled())
                {
                    okButton.setEnabled(false);

                    if (selectedFile == null)
                    {
                        JOptionPane.showMessageDialog(MainFrame.getContext(), "请选择图像文件", "选择图片", JOptionPane.WARNING_MESSAGE);
                        okButton.setEnabled(true);
                        return;
                    }

                    okButton.setIcon(IconUtil.getIcon(this, "/image/sending.gif"));
                    okButton.setText("应用中...");

                    BufferedImage selectedImage = imageLabel.getSelectedImage();
                    if (selectedImage == null)
                    {
                        restoreOKButton();
                    }
                    else
                    {
                    	//刷新缓存以便再次保存新的头像进缓存
                    	AvatarUtil.refreshUserAvatarCache(UserCache.CurrentUserName);
                        // 上传头像到服务器，图片数据为selectedImage
                    	saveVCard(selectedImage);
                        JOptionPane.showMessageDialog(MainFrame.getContext(), "更改头像", "更改头像", JOptionPane.INFORMATION_MESSAGE);
                        okButton.setText("头像已保存");
                        okButton.setIcon(null);
                        //保存到本地
                        AvatarUtil.saveMyAvatar(selectedImage, UserCache.CurrentUserName);
                        MyInfoPanel.getContext().reloadAvatar();
                    }

                }

                super.mouseClicked(e);
            }
        });
    }

    private void chooseImage()
    {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("请选择图片");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("图像", "jpg", "jpeg", "png", "JPG", "JPEG", "PNG"));

        fileChooser.showDialog(MainFrame.getContext(), "上传");
        if (fileChooser.getSelectedFile() != null)
        {
            selectedFile = fileChooser.getSelectedFile();

            String extension = selectedFile.getName();
            if (!extension.endsWith(".jpg") && !extension.endsWith(".jpeg") && !extension.endsWith(".png")
            		 && !extension.endsWith(".JPG") && !extension.endsWith(".JPEG") && !extension.endsWith(".PNG"))
            {
                JOptionPane.showMessageDialog(MainFrame.getContext(), "请选择图像文件", "文件类型不正确", JOptionPane.WARNING_MESSAGE);
                return;
            }

            openImage(selectedFile);

        }
    }

    /**
     * 对图片进行base64编码
     *
     * @param image
     * @return
     */
    public static String base64EncodeImage(BufferedImage image)
    {
        byte[] data = null;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try
        {
            ImageIO.write(image, "png", byteArrayOutputStream);
            data = byteArrayOutputStream.toByteArray();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return new String(Base64.encodeBase64(data));
    }

    public void restoreOKButton()
    {
        okButton.setText("使用头像");
        okButton.setIcon(null);
        okButton.setEnabled(true);
        //selectedFile = null;
    }

    public void showSuccessMessage()
    {
        statusLabel.setVisible(true);
    }

    public static ChangeAvatarPanel getContext()
    {
        return context;
    }
    
    public void saveVCard(BufferedImage selectedImage) {
		DebugUtil.debug("保存Vcard信息");
		
		VCard vcard = null;
		try {
			try {
				vcard = VCardManager.getInstanceFor(Launcher.connection).loadVCard(JidCreate.entityBareFrom(UserCache.CurrentBareJid));
			} catch (XmppStringprepException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (NoResponseException | XMPPErrorException | NotConnectedException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		// Save personal info
//		vcard.setFirstName(personalPanel.getFirstName());
//		vcard.setLastName(personalPanel.getLastName());
//		vcard.setEmailHome(personalPanel.getEmailAddress());
//		vcard.setOrganization(personalPanel.getCompany());
//		vcard.setAddressFieldWork("STREET", personalPanel.getStreetAddress());
//		vcard.setAddressFieldWork("REGION", personalPanel.getState());
//		vcard.setAddressFieldWork("CTRY", personalPanel.getCountry());
//		vcard.setField("TITLE", personalPanel.getJobTitle());
//		vcard.setPhoneWork("VOICE", personalPanel.getPhone());
//		vcard.setPhoneWork("CELL", personalPanel.getMobile());


		// Save Avatar

		Image image = selectedImage;
		byte[] imageInByte;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ImageIO.write( (RenderedImage) image, "jpg", baos );
			baos.flush();
			imageInByte = baos.toByteArray();
			baos.close();
			
			if (image.getWidth(null) > 128 || image.getHeight(null) > 128) {
				image = image.getScaledInstance(-1, 128, Image.SCALE_SMOOTH);
			} 

			if (imageInByte != null) {
				vcard.setAvatar(imageInByte);
			}
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			VCardManager.getInstanceFor(Launcher.connection).saveVCard(vcard);
			// Change my own presence
			//SessionManager().changePresence(newPresence);

		} catch (NoResponseException | NotConnectedException | InterruptedException | XMPPException e) {
			JOptionPane.showMessageDialog(null,
					"服务器不支持VCards。 无法保存你的VCard。",
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

}

class ImageAdjustLabel extends JLabel
{

	private static final long serialVersionUID = -466816743572838261L;
	private BufferedImage image;
    private BufferedImage scaledImage;
    private int imageMaxWidth;
    private int imageMaxHeight;
    private float imageScale = 1.0F; // 宽高比
    private float zoomScale = 1.0F; // 宽度/高度 缩放比
    private BufferedImage tempImage;
    private int anchorWidth = 5;

    private static final int OUTSIDE_SELECTED = -1;
    private static final int IN_SELECTED_AREA = 0;
    private static final int LEFT_TOP = 1;
    private static final int LEFT_BOTTOM = 2;
    private static final int RIGHT_TOP = 3;
    private static final int RIGHT_BOTTOM = 4;

    private Cursor moveCursor;
    private Cursor NWresizeCursor;
    private Cursor SWresizeCursor;
    private Cursor NEresizeCursor;
    private Cursor SEresizeCursor;

    private int mouseDownArea = OUTSIDE_SELECTED;
    private int startX;
    private int startY;

    private boolean mouseDragged = false;
    private int drawX;
    private int drawY;

    private int selectedWidth;
    private int selectedHeight;
    private BufferedImage selectedImage;
    private int imageX;
    private int imageY;
    private int targetWidth;
    private int targetHeight;
    private int minSelectWidth = 80;
    private int imageWidth;
    private int imageHeight;
    private Image initAvatar;
    private boolean isOriginalAvatar = true;

    public ImageAdjustLabel(int imageMaxWidth, int imageMaxHeight, Image initAvatar)
    {
        this.imageMaxWidth = imageMaxWidth;
        this.imageMaxHeight = imageMaxHeight;
        this.initAvatar = initAvatar;

        moveCursor = new Cursor(Cursor.MOVE_CURSOR);
        NWresizeCursor = new Cursor(Cursor.NW_RESIZE_CURSOR);
        SWresizeCursor = new Cursor(Cursor.SW_RESIZE_CURSOR);
        NEresizeCursor = new Cursor(Cursor.NE_RESIZE_CURSOR);
        SEresizeCursor = new Cursor(Cursor.SE_RESIZE_CURSOR);

        setListeners();
        paintInitAvatar();
    }

    private void paintInitAvatar()
    {
        if (this.initAvatar == null)
        {
            return;
        }
    }

    @Override
    public void paint(Graphics g)
    {
        if (isOriginalAvatar)
        {
            int x = (imageMaxWidth - 200) / 2;
            int y = (imageMaxHeight - 200) / 2;
            g.drawImage(initAvatar, x, y, 200, 200, ImageAdjustLabel.this);
        }
        else
        {
            adjustAndPaintOpenedImage((Graphics2D) g.create());
        }

        super.paint(g);
    }

    public void setImage(BufferedImage image)
    {
        isOriginalAvatar = false;
        this.image = image;
    }

    private void adjustAndPaintOpenedImage(Graphics2D g2d)
    {
        if (image == null)
        {
            return;
        }
        imageWidth = image.getWidth(null);
        imageHeight = image.getHeight(null);
        imageScale = imageWidth * 1.0F / imageHeight;
        targetWidth = imageWidth;
        targetHeight = imageHeight;


        if (imageWidth >= imageHeight)
        {
            if (imageWidth > imageMaxWidth)
            {
                targetWidth = imageMaxWidth;
                targetHeight = (int) (imageMaxWidth / imageScale);
            }
        }
        else
        {
            if (imageHeight > imageMaxHeight)
            {
                targetHeight = imageMaxHeight;
                targetWidth = (int) (targetHeight * imageScale);
            }
        }

        // 缩放比例
        zoomScale = targetWidth * 1.0F / imageWidth;

        // 缩放后的图像
        scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        scaledImage.getGraphics().drawImage(image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);

        // 使图片居中显示
        imageX = 0;
        imageY = 0;
        if (targetWidth < imageMaxWidth)
        {
            imageX = (imageMaxWidth - targetWidth) / 2;
        }
        if (targetHeight < imageMaxHeight)
        {
            imageY = (imageMaxHeight - targetHeight) / 2;
        }

        // 添加一层灰色
        RescaleOp ro = new RescaleOp(0.3f, 0, null);
        tempImage = ro.filter(scaledImage, null);
        g2d.drawImage(tempImage, imageX, imageY, targetWidth, targetHeight, null);


        selectedWidth = targetWidth < targetHeight ? targetWidth : targetHeight;
        selectedHeight = selectedWidth;

        drawX = (targetWidth - selectedWidth) / 2;
        drawY = (targetHeight - selectedHeight) / 2;


        g2d.setColor(Colors.LIGHT_GRAY);
        // 绘制选定区域矩形
        g2d.drawRect(drawX + imageX - 1, drawY + imageY - 1, selectedWidth + 1, selectedHeight + 1);
        selectedImage = scaledImage.getSubimage(drawX, drawY, selectedWidth, selectedHeight);
        g2d.drawImage(selectedImage, imageX + drawX, imageY + drawY, null);

        g2d.dispose();
    }

    private void setListeners()
    {
        this.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                mouseDownArea = getMousePosition(e);

                startX = e.getX();
                startY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (mouseDragged)
                {
                    mouseDragged = false;
                }
            }

            /*@Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2)
                {
                    getSelectedImage();
                }
                super.mouseClicked(e);
            }*/
        });

        this.addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseDragged(MouseEvent e)
            {
                if (image == null)
                {
                    return;
                }

                mouseDragged = true;

                int xDistance = 0;
                int yDistance = 0;
                // 如果鼠标落在选定区域内，则鼠标移动时移动选定区域
                if (mouseDownArea == IN_SELECTED_AREA)
                {
                    xDistance = e.getX() - startX;
                    yDistance = e.getY() - startY;

                    drawX += xDistance;
                    drawY += yDistance;

                    drawX = drawX + selectedWidth > targetWidth ? targetWidth - selectedWidth : drawX;
                    drawY = drawY + selectedHeight > targetHeight ? targetHeight - selectedHeight : drawY;

                    startX = e.getX();
                    startY = e.getY();
                }
                // 选定新的区域
                else if (mouseDownArea == OUTSIDE_SELECTED)
                {
                }
                // 落在四个角
                else
                {
                    xDistance = e.getX() - startX;

                    int distance = xDistance;


                    switch (mouseDownArea)
                    {
                        case LEFT_TOP:
                        {
                            selectedWidth -= distance;
                            selectedHeight -= distance;

                            if (selectedWidth >= minSelectWidth)
                            {
                                drawX += distance;
                                drawY += distance;
                            }


                            break;
                        }
                        case LEFT_BOTTOM:
                        {

                            selectedWidth -= distance;
                            selectedHeight -= distance;

                            if (selectedWidth >= minSelectWidth)
                            {
                                drawX += distance;
                            }

                            break;
                        }
                        case RIGHT_TOP:
                        {

                            selectedWidth += distance;
                            selectedHeight += distance;

                            if (selectedWidth >= minSelectWidth)
                            {
                                drawY -= distance;
                            }

                            break;
                        }
                        case RIGHT_BOTTOM:
                        {
                            selectedWidth += distance;
                            selectedHeight += distance;

                            break;
                        }
                    }

                    //selectedWidth = drawX + selectedWidth > targetWidth ? targetWidth - drawX : selectedWidth;
                    //selectedHeight = drawY + selectedHeight > targetHeight ? targetHeight - drawY : selectedHeight;

                    if (drawX + selectedWidth > targetWidth)
                    {
                        selectedWidth = targetWidth - drawX;
                        selectedHeight = selectedWidth;
                    }
                    if (drawY + selectedHeight > targetHeight)
                    {
                        selectedHeight = targetHeight - drawY;
                        selectedWidth = selectedHeight;
                    }
                    selectedWidth = selectedWidth < minSelectWidth ? minSelectWidth : selectedWidth;
                    selectedHeight = selectedHeight < minSelectWidth ? minSelectWidth : selectedHeight;

                    drawX = drawX > targetWidth - selectedWidth ? targetWidth - selectedWidth : drawX;
                    drawY = drawY > targetHeight - selectedHeight ? targetHeight - selectedHeight : drawY;

                    startX = e.getX();
                    startY = e.getY();
                }

                drawSelectedImage();
            }

            @Override
            public void mouseMoved(MouseEvent e)
            {
                int mousePosition = getMousePosition(e);
                switch (mousePosition)
                {
                    case IN_SELECTED_AREA:
                    {
                        setCursor(moveCursor);
                        break;
                    }
                    case LEFT_TOP:
                    {
                        setCursor(NWresizeCursor);
                        break;
                    }
                    case LEFT_BOTTOM:
                    {
                        setCursor(SWresizeCursor);
                        break;
                    }
                    case RIGHT_TOP:
                    {
                        setCursor(NEresizeCursor);
                        break;
                    }
                    case RIGHT_BOTTOM:
                    {
                        setCursor(SEresizeCursor);
                        break;
                    }
                }

                super.mouseMoved(e);
            }
        });

        this.addMouseWheelListener(new MouseAdapter()
        {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (e.getWheelRotation() < 0)
                {
                    drawX -= 5;
                    drawY -= 5;
                    selectedWidth += 10;
                    selectedHeight += 10;


                    drawX = drawX < 0 ? 0 : drawX;
                    drawY = drawY < 0 ? 0 : drawY;
                }
                else
                {

                    selectedWidth -= 10;
                    selectedHeight -= 10;

                    if (selectedWidth >= minSelectWidth)
                    {
                        drawX += 5;
                        drawY += 5;
                    }
                }

                if (drawX + selectedWidth > targetWidth)
                {
                    selectedWidth = targetWidth - drawX;
                    selectedHeight = selectedWidth;
                }
                if (drawY + selectedHeight > targetHeight)
                {
                    selectedHeight = targetHeight - drawY;
                    selectedWidth = selectedHeight;
                }
                selectedWidth = selectedWidth < minSelectWidth ? minSelectWidth : selectedWidth;
                selectedHeight = selectedHeight < minSelectWidth ? minSelectWidth : selectedHeight;

                drawX = drawX > targetWidth - selectedWidth ? targetWidth - selectedWidth : drawX;
                drawY = drawY > targetHeight - selectedHeight ? targetHeight - selectedHeight : drawY;

                drawSelectedImage();
                super.mouseWheelMoved(e);
            }
        });

    }

    private void drawSelectedImage()
    {
        drawX = drawX < 0 ? 0 : drawX;
        drawY = drawY < 0 ? 0 : drawY;

        Image tempImage2 = createImage(targetWidth, targetHeight);
        Graphics g = tempImage2.getGraphics();
        g.drawImage(tempImage, 0, 0, null);

        g.setColor(Colors.LIGHT_GRAY);
        // 绘制选定区域矩形
        g.drawRect(drawX - 1, drawY - 1, selectedWidth + 1, selectedHeight + 1);

        // 绘制四角锚点
        g.fillRect(drawX - anchorWidth, drawY - anchorWidth, anchorWidth, anchorWidth);
        g.fillRect(drawX + selectedWidth, drawY - anchorWidth, anchorWidth, anchorWidth);
        g.fillRect(drawX - anchorWidth, drawY + selectedHeight, anchorWidth, anchorWidth);
        g.fillRect(drawX + selectedWidth, drawY + selectedHeight, anchorWidth, anchorWidth);

        selectedWidth = selectedWidth > targetWidth ? targetWidth : selectedWidth;
        selectedHeight = selectedHeight > targetHeight ? targetHeight : selectedHeight;
        selectedImage = scaledImage.getSubimage(drawX, drawY, selectedWidth, selectedHeight);
        g.drawImage(selectedImage, drawX, drawY, null);

        ImageAdjustLabel.this.getGraphics().drawImage(tempImage2, imageX, imageY, ImageAdjustLabel.this);
    }


    private int getMousePosition(MouseEvent e)
    {
        int x = e.getX();
        int y = e.getY();

        if (x >= drawX + imageX && x <= drawX + imageX + selectedWidth && y >= drawY + imageY && y <= drawY + imageY + selectedHeight)
        {
            return IN_SELECTED_AREA;
        }
        else if (x >= drawX + imageX - anchorWidth && x <= drawX + imageX && y >= drawY + imageY - anchorWidth && y <= drawY + imageY)
        {
            return LEFT_TOP;
        }
        else if (x >= drawX + imageX + selectedWidth && x <= drawX + imageX + selectedWidth + anchorWidth && y >= drawY + imageY - anchorWidth && y <= drawY + imageY)
        {
            return RIGHT_TOP;
        }
        else if (x >= drawX + imageX - anchorWidth && x <= drawX + imageX && y >= drawY + imageY + selectedHeight && y <= drawY + imageY + selectedHeight + anchorWidth)
        {
            return LEFT_BOTTOM;
        }
        else if (x >= drawX + imageX + selectedWidth && x <= drawX + imageX + selectedWidth + anchorWidth && y >= drawY + imageY + selectedHeight && y <= drawY + imageY + selectedHeight + anchorWidth)
        {
            return RIGHT_BOTTOM;
        }
        else
        {
            return OUTSIDE_SELECTED;
        }
    }

    public void setInitAvatar(Image initAvatar)
    {
        this.initAvatar = initAvatar;
    }

    public BufferedImage getSelectedImage()
    {
        try
        {
            int x = (int) (drawX / zoomScale);
            int y = (int) (drawY / zoomScale);
            int w = (int) (selectedWidth / zoomScale);
            int h = (int) (selectedHeight / zoomScale);

            x = x + w > imageWidth ? imageWidth - w : x;
            y = y + h > imageHeight ? imageHeight - h : y;

            BufferedImage selectedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            selectedImage.getGraphics().drawImage(image.getSubimage(x, y, w, h), 0, 0, w, h, null);

            BufferedImage outputImage = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
            outputImage.getGraphics().drawImage(selectedImage.getScaledInstance(80, 80, Image.SCALE_SMOOTH), 0, 0, null);

            return outputImage;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
