package local.quiettv.home;

import android.graphics.Color;

/** Four complete palettes, including geometry, contrast and native dialog theme. */
final class HomeTheme {
    final int index, background, surface, accent, foreground, muted, radius, actionRadius, style;
    final boolean light;
    private HomeTheme(int i, String bg, String panel, String highlight, String fg, String secondary,
                      int corners, int actionCorners, int resource, boolean isLight) {
        index=i; background=Color.parseColor(bg); surface=Color.parseColor(panel);
        accent=Color.parseColor(highlight); foreground=Color.parseColor(fg); muted=Color.parseColor(secondary);
        radius=corners; actionRadius=actionCorners; style=resource; light=isLight;
    }
    static HomeTheme get(int index) {
        switch(index) {
            case 1: return new HomeTheme(1,"#121812","#26352B","#83D6A3","#EFF7F1","#ADBDB2",18,26,R.style.QuietGreenTheme,false);
            case 2: return new HomeTheme(2,"#EDF2F9","#DAE3F0","#315EC4","#17213B","#52627A",20,18,R.style.QuietLightTheme,true);
            case 3: return new HomeTheme(3,"#201B18","#39302A","#D9B17C","#F7F1E9","#C5B6A4",6,8,R.style.QuietWarmTheme,false);
            default: return new HomeTheme(0,"#101827","#25334B","#91B7FF","#EFF4FF","#ADBCD4",12,14,R.style.QuietTheme,false);
        }
    }
    static int mix(int a,int b,float amount) {
        float t=Math.max(0,Math.min(1,amount));
        return Color.rgb(Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
            Math.round(Color.green(a)*(1-t)+Color.green(b)*t),Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
}
