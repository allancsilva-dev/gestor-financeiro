declare module 'expo-audio' {
  export const RecordingPresets: { HIGH_QUALITY: object };
  export const AudioModule: { requestRecordingPermissionsAsync(): Promise<{ granted: boolean }> };
  export function setAudioModeAsync(options: { allowsRecording: boolean; playsInSilentMode?: boolean }): Promise<void>;
  export interface AudioRecorder {
    isRecording: boolean;
    uri: string | null;
    prepareToRecordAsync(): Promise<void>;
    record(options?: { forDuration?: number }): void;
    stop(): Promise<void>;
  }
  export function useAudioRecorder(options: object): AudioRecorder;
  export function useAudioRecorderState(recorder: AudioRecorder, interval?: number): {
    isRecording: boolean; durationMillis: number;
  };
}
