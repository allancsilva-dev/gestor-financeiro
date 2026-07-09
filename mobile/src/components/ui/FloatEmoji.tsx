import React, { useEffect, useRef } from 'react';
import { AccessibilityInfo, Animated, Easing, Text } from 'react-native';

interface FloatEmojiProps {
  children: string;
  fontSize?: number;
}

// Emoji flutuando suavemente (gf-float do protótipo, ex: 👋 da home).
// Com Reduce Motion ativo, fica estático.
export default function FloatEmoji({ children, fontSize = 20 }: FloatEmojiProps) {
  const anim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    let loop: Animated.CompositeAnimation | null = null;
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then(reduce => {
      if (!mounted || reduce) return;
      loop = Animated.loop(
        Animated.sequence([
          Animated.timing(anim, { toValue: 1, duration: 1200, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
          Animated.timing(anim, { toValue: 0, duration: 1200, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
        ])
      );
      loop.start();
    });
    return () => { mounted = false; loop?.stop(); };
  }, [anim]);

  return (
    <Animated.View style={{ transform: [{ translateY: anim.interpolate({ inputRange: [0, 1], outputRange: [0, -3] }) }] }}>
      <Text style={{ fontSize }}>{children}</Text>
    </Animated.View>
  );
}
