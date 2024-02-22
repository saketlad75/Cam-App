import base64
import io
import cv2
import numpy as np
import matplotlib.pyplot as plt
import math
import mediapipe as mp
import os
import time
from PIL import Image

def main(data):
    decodedData = base64.b64decode(data)
    npData = np.fromstring(decodedData, np.uint8)
    imge = cv2.imdecode(npData, cv2.IMREAD_UNCHANGED)
    # masked_image = return_masked_image(imge, 0.1)
    # masked_image = masked_image.astype(np.uint8)
    pil_im = Image.fromarray(imge)
    buff = io.BytesIO()
    pil_im.save(buff, format="PNG")
    img_str = base64.b64encode(buff.getvalue())
    return "" + str(img_str, 'utf-8')

def scale_and_resize(image, scale_factor=1.0, target_width=None):
    """
    Scale and resize the input image.

    Parameters:i
    - image: numpy.ndarray, input image.
    - scale_factor: float, scaling factor for the image.
    - target_width: int or None, target width after resizing. If None, target_width is not applied.

    Returns:
    - resized_image: numpy.ndarray, scaled and resized image.
    """
    if scale_factor <= 0:
        raise ValueError("Scale factor must be greater than 0.")

    if target_width is not None and target_width <= 0:
        raise ValueError("Target width must be greater than 0.")

    # Calculate the new height based on the scale factor
    new_height = int(image.shape[0] * scale_factor)

    if target_width is not None:
        # Resize to the target width while maintaining the original aspect ratio
        aspect_ratio = image.shape[1] / image.shape[0]
        new_width = target_width
        new_height = int(new_width / aspect_ratio)
    else:
        new_width = int(image.shape[1] * scale_factor)

    # Perform the resizing
    resized_image = cv2.resize(image, (new_width, new_height))

    return resized_image

class handDetector():
#This class has methods: findHands,findPosition
    def __init__(self, mode=False, maxHands=1, modelComplexity=1, detectionCon=0.5, trackCon=0.5):
        self.mode = mode
        self.maxHands = maxHands
        self.modelComplex = modelComplexity
        self.detectionCon = detectionCon
        self.trackCon = trackCon

        self.mpHands = mp.solutions.hands
        self.hands = self.mpHands.Hands(self.mode, self.maxHands, self.modelComplex,
                                        self.detectionCon, self.trackCon)
        self.mpDraw = mp.solutions.drawing_utils
        self.results = []

    def return_hand_type(self):
        for id, classification in enumerate(self.results.multi_handedness):
          label = classification.classification[0].label
          score = classification.classification[0].score
        return label, score

    def findHands(self,img,draw=True):
        #imgRGB = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        self.results = self.hands.process(img)
        point1 = [handLms.landmark for handLms in self.results.multi_hand_landmarks]


        if self.results.multi_hand_landmarks:
            for handLms in self.results.multi_hand_landmarks: #gives result for all(2) hands
            #handLMs are 21 points. so we need conection too-->mpHands.HAND_CONNECTIONS
                if draw: # if draw=True
                    self.mpDraw.draw_landmarks(img, handLms, self.mpHands.HAND_CONNECTIONS) #drawing points and lines(=handconections)
        return img

    def findPosition(self, img, handNo=0, draw=False):
        lmlist=[] #landmark list
        if self.results.multi_hand_landmarks:
            myHand= self.results.multi_hand_landmarks[handNo] #selecting specific hand, != all hand
            points = []
            for id, lm in enumerate(myHand.landmark):#landamarks for selected hand
                # print(id, lm)
                #lm = x,y cordinate of each landmark in float numbers. lm.x, lm.y methods
                #So, need to covert in integer
                h, w, c =img.shape
                if id == 0 or id == 5 or id == 17:
                  points.append(np.asarray([lm.x, lm.y, lm.z]))
                cx, cy = int(lm.x * w), int(lm.y * h)
                #print(id, cx, cy)
                lmlist.append([cx,cy])
                if draw:
                    cv2.circle(img, (cx, cy), 15, (0, 255, 0), cv2.FILLED)

        return lmlist, points

def extract_roi(img, lmlist, handType):
    point5, point17= lmlist[5], lmlist[17]
    distance_5_17 = np.linalg.norm(np.array(point5) - np.array(point17))

    # Calculate unit vector from point 5 to point 17
    unit_vector_5_17 = (np.array(point17) - np.array(point5)) / distance_5_17

    # Calculate perpendicular vectors in the direction of point0
    perpendicular_vector_1 = np.array([unit_vector_5_17[1], -unit_vector_5_17[0]])
    if handType == "Right": perpendicular_vector_1 *= -1

    perpendicular_vector_2 = perpendicular_vector_1  # Ensure it points in the opposite direction


    # Calculate points below point 5 and point 17
    point_below_5 = point5 + (perpendicular_vector_1 * distance_5_17).astype(int)
    point_below_17 = point17 + (perpendicular_vector_2 * distance_5_17).astype(int)
    # Calculate the average x and y coordinates
    center_x = (point5[0] + point17[0] + point_below_5[0] + point_below_17[0]) / 4
    center_y = (point5[1] + point17[1] + point_below_5[1] + point_below_17[1]) / 4

    # Convert to integer if needed
    center_point = (int(center_x), int(center_y))
    # Mark the points on the image
    # cv2.circle(img, point5, 10, (0, 0, 255), -1)
    # cv2.circle(img, point17, 10, (0, 0, 255), -1)
    # cv2.circle(img, tuple(point_below_5), 10, (0, 0, 255), -1)
    # cv2.circle(img, tuple(point_below_17), 10, (0, 0, 255), -1)
    # cv2.circle(img, center_point, 5, (255, 0, 0), -1)
    # cv2.line(img, center_point, (int((point5[0] + point17[0]) / 2), int((point5[1] + point17[1]) / 2)), (0, 255, 0), 2)
    # cv2.line(img, center_point, (center_point[0], 0), (0, 0, 255), 2)
    # Convert the points to a numpy array
    roi_pts = np.array([point5, point17, point_below_17, point_below_5], np.int32)

    # Reshape the array to a 4x1x2 array
    roi_pts = roi_pts.reshape((-1, 1, 2))
    # print(roi_pts)
    # Define the coordinates of the rectangle
    # Find the bounding rectangle
    # rect = cv2.boundingRect(roi_pts)
    return img, roi_pts, center_point

# def calculate_angle(point5, point17, center_point):
#     vector_line1 = np.array([(int((point5[0] + point17[0]) / 2) - center_point[0]), (int((point5[1] + point17[1]) / 2) - center_point[1])])
#     vector_line2 = np.array([0, -1])  # Assuming a vertical line parallel to the y-axis

#     # Calculate the dot product
#     dot_product = np.dot(vector_line1, vector_line2)

#     # Calculate the magnitudes of the vectors
#     magnitude_line1 = np.linalg.norm(vector_line1)
#     magnitude_line2 = np.linalg.norm(vector_line2)

#     # Calculate the cosine of the angle
#     cosine_angle = dot_product / (magnitude_line1 * magnitude_line2)

#     # Calculate the angle in radians
#     angle_radians = np.arccos(cosine_angle)

#     # Convert the angle to degrees
#     angle_degrees = np.degrees(angle_radians)

#     # Display the result
#     print(f"Angle between the two lines: {angle_degrees} degrees")

#     return angle_degrees

def roi_mask(img, roi_pts):
    # Draw the rectangle on the image
    mask = np.zeros_like(img)
    # Set the region inside the polygon to zero in the original image
    cv2.fillPoly(mask, [roi_pts], color=(255, 255, 255))

    # Bitwise AND to get the ROI
    roi = cv2.bitwise_and(img, mask)
    return roi

def warp_affine_image(src, angle):
    center = (src.shape[1] // 2, src.shape[0] // 2)
    rotation_matrix = cv2.getRotationMatrix2D(center, angle, scale=1.0)
    rotated_image = cv2.warpAffine(src, rotation_matrix, (src.shape[1], src.shape[0]))
    return rotated_image

def display_image_from_4_points(image, src_points):
    # Define the destination points (assuming a rectangular output)
    src_points = np.array(src_points, dtype=np.float32)
    dst_points = np.array([[0, 0], [0, 128], [128, 128], [128, 0]], dtype=np.float32)


    # Calculate the perspective transformation matrix
    matrix = cv2.getPerspectiveTransform(src_points, dst_points)

    # Apply the perspective transformation to the image
    result = cv2.warpPerspective(image, matrix, (128, 128))
    return result

def apply_adaptive_mean_thresholding(image, method="GAUSSIAN", block_size=7, subtraction_const=1):
    """NOTE: block size must be an odd number"""
    image_gray = cv2.cvtColor((image * 255).astype(np.uint8), cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=5)
    final_img = clahe.apply(image_gray) + 30
    if method == "GAUSSIAN":
        thresholded_image = cv2.adaptiveThreshold(final_img, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                                  cv2.THRESH_BINARY, block_size, subtraction_const)
    elif method == "MEAN":
        thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                                                  cv2.THRESH_BINARY, block_size, subtraction_const)
    else:
        print("Wrong method name used!")
        return


    return thresholded_image.astype(np.float64) / 255

def gabor_filter(img, ksize, sigma, theta, Lambda, gamma):
    gabor_kernel = cv2.getGaborKernel((ksize, ksize), sigma, theta, Lambda, gamma, 0, ktype=cv2.CV_64F)
    gabor_output = cv2.filter2D(img, cv2.CV_64F, gabor_kernel)
    return gabor_output

directory = "/content/drive/MyDrive/Palm"
imgs = []
print(os.listdir(directory))
for img_name in sorted(os.listdir(directory)):
    print(f"{directory}/{img_name}")
    img_original = cv2.imread(f"{directory}/{img_name}")
    start = time.time()
    detector = handDetector()
    resized = scale_and_resize(img_original, 0.1, 512)
    img = detector.findHands(resized)
    lmlist, points = detector.findPosition(img)
    handType = detector.return_hand_type()[0]
    roi_marked, roi_pts, center_point = extract_roi(img, lmlist, handType)
    roi = roi_mask(resized, roi_pts)
    warped = display_image_from_4_points(roi, roi_pts)
    rotated_image = cv2.transpose(warped)
    if handType == "Left":
        rotated_image = cv2.flip(rotated_image, flipCode=1)
    # threshold_image = apply_adaptive_mean_thresholding(rotated_image, "GAUSSIAN", 5)
    roi_gray = cv2.cvtColor(rotated_image, cv2.COLOR_BGR2GRAY)


    # print(time.time() - start)
    # plt.figure(figsize=(20, 20))
    # plt.subplot(1, 4, 1)
    # plt.imshow(img_original, cmap='gray')
    # plt.subplot(1, 4, 2)
    # plt.imshow(roi_marked, cmap='gray')
    # plt.subplot(1, 4, 3)
    # plt.imshow(rotated_image, cmap='gray')
    # plt.subplot(1, 4, 4)
    # # plt.imshow(gabor_output_rgb, cmap='gray')
    # # plt.show()

