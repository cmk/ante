#pragma once

#include "assert.hpp"
#include "Util/function.hpp"

namespace assert
{
func::function<AssertBreakPoint (const AssertContext &)> SetAssertCallback(func::function<AssertBreakPoint (const AssertContext &)>);
const func::function<AssertBreakPoint (const AssertContext &)> & GetAssertCallback();
struct ScopedSetAssertCallback
{
	ScopedSetAssertCallback(func::function<AssertBreakPoint (const AssertContext &)> callback)
		: old_callback(SetAssertCallback(std::move(callback)))
	{
	}
	~ScopedSetAssertCallback()
	{
		SetAssertCallback(std::move(old_callback));
	}

private:
	func::function<AssertBreakPoint (const AssertContext &)> old_callback;
};
}
