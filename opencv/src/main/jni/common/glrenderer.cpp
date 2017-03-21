/*
 * By downloading, copying, installing or using the software you agree to this license.
 * If you do not agree to this license, do not download, install,
 * copy or use the software.
 *
 *
 *                           License Agreement
 *                        (3-clause BSD License)
 *
 * Copyright (C) 2014-2017, saki t_saki@serenegiant.com
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *   * Neither the names of the copyright holders nor the names of the contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * This software is provided by the copyright holders and contributors "as is" and
 * any express or implied warranties, including, but not limited to, the implied
 * warranties of merchantability and fitness for a particular purpose are disclaimed.
 * In no event shall copyright holders or contributors be liable for any direct,
 * indirect, incidental, special, exemplary, or consequential damages
 * (including, but not limited to, procurement of substitute goods or services;
 * loss of use, data, or profits; or business interruption) however caused
 * and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of
 * the use of this software, even if advised of the possibility of such damage.
 */

#define LOG_TAG "GLRenderer"
#if 1	// デバッグ情報を出さない時は1
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// LOGV/LOGD/MARKを出力しない時
	#endif
	#undef USE_LOGALL			// 指定したLOGxだけを出力
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
	#undef NDEBUG
	#define DEBUG_GL_CHECK			// GL関数のデバッグメッセージを表示する時
#endif

#include <string.h>

#include "utilbase.h"
#include "glutils.h"
#include "glrenderer.h"
#include "glprogram.h"

GLRenderer::GLRenderer(const char *pVertexSource, const char *pFragmentSource)
:	mShaderProgram(createShaderProgram(
		pVertexSource, pFragmentSource, &mVertexShader, &mFragmentShader)),
	mBrightness(0.0f) {

	ENTER();

	init();

	EXIT();
}

GLRenderer::GLRenderer(Assets &assets, const char *vertexfile, const char *fragmentfile)
: 	mShaderProgram(createShaderProgram(
 		assets, vertexfile, fragmentfile, &mVertexShader, &mFragmentShader)),
	mBrightness(0.0f) {

	ENTER();

	init();

	EXIT();
}

GLRenderer::~GLRenderer() {
	ENTER();

	disposeProgram(mShaderProgram, mVertexShader, mFragmentShader);

	EXIT();
}

void GLRenderer::init() {
	ENTER();
    // attribute変数のロケーションを取得
    maPositionLoc = glGetAttribLocation(mShaderProgram, "aPosition");
    GLCHECK("glGetAttribLocation:maPositionLoc");
    maTextureCoordLoc = glGetAttribLocation(mShaderProgram, "aTextureCoord");
    GLCHECK("glGetAttribLocation:maTextureCoordLoc");
    // uniform変数のロケーションを取得
    muMVPMatrixLoc = glGetUniformLocation(mShaderProgram, "uMVPMatrix");
    GLCHECK("glGetUniformLocation:muMVPMatrixLoc");
    if (muMVPMatrixLoc < 0)
    	LOGW("muMVPMatrixLoc undefined");
    muTexMatrixLoc = glGetUniformLocation(mShaderProgram, "uTexMatrix");
    GLCHECK("glGetUniformLocation:muTexMatrixLoc");
    if (muTexMatrixLoc < 0)
    	LOGW("muTexMatrixLoc undefined");
    muTextureSzLoc = glGetUniformLocation(mShaderProgram, "uTextureSz");
    GLCHECK("glGetUniformLocation:muTextureSzLoc");
    if (muTextureSzLoc < 0)
    	LOGW("muTextureSzLoc undefined");
	muFrameSzLoc =  glGetUniformLocation(mShaderProgram, "uFrameSz");
	GLCHECK("glGetUniformLocation:muFrameSzLoc");
	if (muFrameSzLoc < 0)
	 	LOGD("uFrameSz undefined");	// これはフラグメントシェーダー内で使ってないので最適化でなくなってしまうみたい
    muBrightnessLoc = glGetUniformLocation(mShaderProgram, "uBrightness");
    GLCHECK("glGetUniformLocation:muBrightnessLoc");
    if (muBrightnessLoc < 0)
    	LOGD("muBrightnessLoc undefined");
    EXIT();
}


#define COORD_NUM 2	// テクスチャ座標・頂点座標1点あたりの要素の数
static const GLfloat FULL_RECTANGLE_COORDS[] = {
	-1.0f,  1.0f,	// 2 top left
	1.0f,  1.0f,	// 3 top right
	-1.0f, -1.0f,	// 0 bottom left
	1.0f, -1.0f,	// 1 bottom right
};

// テクスチャ座標(上下反転)
static const GLfloat FULL_RECTANGLE_TEX_COORDS[] = {
	0.0f, 0.0f,		// 0 bottom left
	1.0f, 0.0f,		// 1 bottom right
	0.0f, 1.0f,		// 2 top left
	1.0f, 1.0f		// 3 top right
};

const static int  VERTEX_NUM = NUM_ARRAY_ELEMENTS(FULL_RECTANGLE_COORDS) / COORD_NUM;	// 2個で1頂点なので1/2

// yuyvをrgbaに対応させる(2ピクセルの元データをテクスチャ1テクセルに代入する)時はview_widthを1/2にして呼び出すこと
int GLRenderer::draw(GLTexture *texture, const GLfloat *tex_matrix, const GLfloat *mvp_matrix) {
	ENTER();

	// プログラムシェーダーを選択する
	glUseProgram(mShaderProgram);
    GLCHECK("draw:glUseProgram");
    texture->bind();
    // テクスチャサイズをセット
    if (LIKELY(muTextureSzLoc >= 0)) {
        glUniform2f(muTextureSzLoc, texture->getTexWidth(), texture->getTexHeight());
        GLCHECK("glUniform2f:muTextureSzLoc");
    }
	if (LIKELY(muFrameSzLoc >= 0)) {
		glUniform2f(muFrameSzLoc, texture->getImageWidth(), texture->getImageHeight());
		GLCHECK("glUniform2f:muFrameSzLoc");
	}
    // 明るさのオフセットをセット
    if (muBrightnessLoc) {
        glUniform1f(muBrightnessLoc, mBrightness);
        GLCHECK("glUniform1f:muBrightnessLoc");
    }

    // モデルビュー・射影行列をセット
    glUniformMatrix4fv(muMVPMatrixLoc, 1, GL_FALSE, mvp_matrix);
    GLCHECK("glUniformMatrix4fv:muMVPMatrixLoc");

    // テクスチャ行列をセット
    if (!tex_matrix)
    	tex_matrix = texture->getTexMatrix();
    glUniformMatrix4fv(muTexMatrixLoc, 1, GL_FALSE, tex_matrix);
    GLCHECK("glUniformMatrix4fv:muTexMatrixLoc");

    // 頂点座標配列"aPosition"を有効にする
    glEnableVertexAttribArray(maPositionLoc);
    GLCHECK("glEnableVertexAttribArray:maPositionLoc");
    // 頂点座標配列を"aPosition"へセット
    glVertexAttribPointer(maPositionLoc, COORD_NUM,
    	GL_FLOAT, GL_FALSE, sizeof(GLfloat) * COORD_NUM, FULL_RECTANGLE_COORDS);
    GLCHECK("glVertexAttribPointer:maPositionLoc");

    // テクスチャ座標配列"aTextureCoord"を有効にする
    glEnableVertexAttribArray(maTextureCoordLoc);
    GLCHECK("glEnableVertexAttribArray(maTextureCoordLoc)");
    // テクスチャ座標配列を"aTextureCoord"へセット
    glVertexAttribPointer(maTextureCoordLoc, COORD_NUM,
    	GL_FLOAT, GL_FALSE, sizeof(GLfloat) * COORD_NUM, FULL_RECTANGLE_TEX_COORDS);
	GLCHECK("glVertexAttribPointer");
    // 矩形を描画
    glDrawArrays(GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
    GLCHECK("glDrawArrays");

    // 終了・・・頂点配列・テクスチャ・シェーダーを無効にする
    glDisableVertexAttribArray(maPositionLoc);
    GLCHECK("glDisableVertexAttribArray:maPositionLoc");
    glDisableVertexAttribArray(maTextureCoordLoc);
    GLCHECK("glDisableVertexAttribArray:maTextureCoordLoc");
    texture->unbind();
    glUseProgram(0);
    GLCHECK("glUseProgram");

    RETURN(0, int);
}
