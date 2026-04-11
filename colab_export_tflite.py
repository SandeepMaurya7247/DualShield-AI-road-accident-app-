import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.preprocessing import StandardScaler

# --- 1. LOAD YOUR DATASET IN COLAB ---
# NOTE: Make sure 'road_accident_imu_dataset_8000.xlsx' is uploaded to your Colab /content/ folder!
print("Loading dataset...")
df = pd.read_excel('road_accident_imu_dataset_8000.xlsx')

# --- 2. PREPROCESS AS PER YOUR NOTEBOOK ---
def add_physics_features(df):
    df = df.copy()
    df["acc_mag"] = np.sqrt(df["Acc_X"]**2 + df["Acc_Y"]**2 + df["Acc_Z"]**2)
    df["gyro_mag"] = np.sqrt(df["Gyro_X"]**2 + df["Gyro_Y"]**2 + df["Gyro_Z"]**2)
    df["jerk"] = df["acc_mag"].diff().fillna(0)
    return df

df = add_physics_features(df)
feature_cols = ["Acc_X", "Acc_Y", "Acc_Z", "Gyro_X", "Gyro_Y", "Gyro_Z", "Speed_kmh", "acc_mag", "gyro_mag", "jerk"]
X = df[feature_cols].to_numpy()
y = df["label"].to_numpy()

# --- 3. FIT THE SCALER ---
scaler = StandardScaler()
X = scaler.fit_transform(X)

# --- IMPORTANT: EXPORT SCALER VALUES FOR ANDROID KOTLIN ---
print("\n" + "="*50)
print("👉 COPY THESE VALUES FOR ANDROID KOTLIN INTEGRATION:")
print(f"val SCALER_MEANS = floatArrayOf({', '.join(map(lambda x: str(x) + 'f', scaler.mean_))})")
print(f"val SCALER_SCALES = floatArrayOf({', '.join(map(lambda x: str(x) + 'f', scaler.scale_))})")
print("="*50 + "\n")

# --- 4. CREATE TIME WINDOWS (Size=20) ---
def create_windows(X, y, window_size=20):
    X_windows, y_windows = [], []
    for i in range(len(X) - window_size):
        X_windows.append(X[i:i + window_size])
        y_windows.append(y[i + window_size])
    return np.array(X_windows), np.array(y_windows)

WINDOW_SIZE = 20
X_seq, y_seq = create_windows(X, y, WINDOW_SIZE)

# Ensure everything is Float32 for TFLite compatibility!
X_seq = X_seq.astype(np.float32)

from tensorflow.keras.utils import to_categorical
from sklearn.model_selection import train_test_split
y_seq_cat = to_categorical(y_seq, 5)

X_train, X_test, y_train, y_test = train_test_split(X_seq, y_seq_cat, test_size=0.2, random_state=42, stratify=y_seq)

# --- 5. BUILD AND TRAIN THE 1D CNN MODEL ---
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv1D, MaxPooling1D, Dense, Dropout, Flatten

print("Training the CNN Model...")
model = Sequential([
    Conv1D(64, 3, activation="relu", input_shape=(WINDOW_SIZE, X_train.shape[2])),
    MaxPooling1D(2),
    Conv1D(128, 3, activation="relu"),
    MaxPooling1D(2),
    Flatten(),
    Dense(128, activation="relu"),
    Dropout(0.5),
    Dense(5, activation="softmax")
])

model.compile(optimizer="adam", loss="categorical_crossentropy", metrics=["accuracy"])
model.fit(X_train, y_train, epochs=20, batch_size=64, verbose=1)

# --- 6. EXPORT TO TF-LITE (.tflite) ---
print("\nExporting to TensorFlow Lite format...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Optimizations for mobile execution
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]

tflite_model = converter.convert()

with open('accident_model.tflite', 'wb') as f:
    f.write(tflite_model)

print("✅ 'accident_model.tflite' has been generated successfully!")
print("Download it from the Colab file browser (left sidebar) and upload it here, so I can put it into the Android project.")
