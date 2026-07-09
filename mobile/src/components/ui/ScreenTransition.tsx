import React, { useCallback, useRef } from 'react';
import { AccessibilityInfo, Animated, Easing, StyleProp, ViewStyle } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';

interface ScreenTransitionProps {
  style?: StyleProp<ViewStyle>;
  children: React.ReactNode;
}

export default function ScreenTransition({ style, children }: ScreenTransitionProps) {
  const progress = useRef(new Animated.Value(0)).current;

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;

      AccessibilityInfo.isReduceMotionEnabled().then(reduce => {
        if (cancelled) return;
        progress.stopAnimation();
        progress.setValue(reduce ? 1 : 0);
        if (reduce) return;

        Animated.timing(progress, {
          toValue: 1,
          duration: 340,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }).start();
      });

      return () => {
        cancelled = true;
      };
    }, [progress])
  );

  return (
    <Animated.View
      style={[
        { flex: 1 },
        style,
        {
          opacity: progress,
          transform: [{
            translateY: progress.interpolate({
              inputRange: [0, 1],
              outputRange: [12, 0],
            }),
          }],
        },
      ]}
    >
      {children}
    </Animated.View>
  );
}
