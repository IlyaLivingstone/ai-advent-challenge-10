package com.ilyalivingstone.aiadvent.day08;

public final class SyntheticStory {
    private SyntheticStory() {
    }

    public static String generate() {
        String line = "Жили-были старик со старухой. Испекли они Колобка, а он покатился по дороге, "
                + "встретил зайца, волка, медведя и лису. Каждый спрашивал: «Колобок, куда катишься?» "
                + "Колобок пел свою песенку и катился дальше. ";
        return line.repeat(13000);
    }
}
