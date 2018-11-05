/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package minesweeperfx;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;

public class Resources {

    public enum ImageType { Blank,
                            Over,
                            ExposedTile,
                            Flag,
                            FlagOver,
                            HitMine,
                            Mine,
                            WrongMine,
                            Number1,
                            Number2,
                            Number3,
                            Number4,
                            Number5,
                            Number6,
                            Number7,
                            Number8 }

    private Map<ImageType, Image> images;

    private static Resources resources;

    public static Resources getInstance() {
        if (resources == null) {
            resources = new Resources();
        }

        return resources;
    }

    private Resources() {
        images = new HashMap<>();
        loadImages();
    }

    private void loadImages() {
        loadImage("blank.png",     ImageType.Blank);
        loadImage("blankover.png", ImageType.Over);
        loadImage("exposed.png",   ImageType.ExposedTile);
        loadImage("flag.png",      ImageType.Flag);
        loadImage("flag.png",      ImageType.FlagOver);
        loadImage("hitmine.png",   ImageType.HitMine);
        loadImage("mine.png",      ImageType.Mine);
        loadImage("wrongmine.png", ImageType.WrongMine);
        loadImage("number1.png",   ImageType.Number1);
        loadImage("number2.png",   ImageType.Number2);
        loadImage("number3.png",   ImageType.Number3);
        loadImage("number4.png",   ImageType.Number4);
        loadImage("number5.png",   ImageType.Number5);
        loadImage("number6.png",   ImageType.Number6);
        loadImage("number7.png",   ImageType.Number7);
        loadImage("number8.png",   ImageType.Number8);
    }

    private void loadImage(String imageName, ImageType type) {
        Image image = new Image(imageName);
        images.put(type, image);
    }

    public Image getImage(ImageType value) {
        return images.get(value);
    }
}
